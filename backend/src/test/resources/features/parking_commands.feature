Feature: Parking commands
  Live parking behavior is driven by commands invoked on domain objects:
  availability queries on lots and state changes on slots.

  Background:
    Given an operator "owner@bdd.org" with password "Owner@1" exists
    And an end user "driver@bdd.org" with password "Drive@1" exists
    And "owner@bdd.org" creates a parking lot named "BDD Lot" in "Tel Aviv"
    And "owner@bdd.org" adds a section "Section A" to the lot
    And "owner@bdd.org" adds a "REGULAR" slot "A-1" with status "FREE" to the section
    And "owner@bdd.org" adds a "REGULAR" slot "A-2" with status "OCCUPIED" to the section
    And "owner@bdd.org" adds a "HANDICAP" slot "A-3" with status "FREE" to the section

  Scenario: Checking availability in a lot
    When the command "availabilityInLot" is invoked on the lot
    Then the availability result reports 2 free slots

  Scenario: Checking handicap availability in a lot
    When the command "availabilityInLot" with filter "HANDICAP" is invoked on the lot
    Then the availability result reports 1 free slot

  Scenario: Occupying a slot updates the live state
    When the command "updateSlotState" with new state "OCCUPIED" is invoked on slot "A-1"
    Then slot "A-1" has status "OCCUPIED"
    When the command "availabilityInLot" is invoked on the lot
    Then the availability result reports 1 free slot

  Scenario: A command must identify its invoker
    When the command "availabilityInLot" is invoked on the lot without an invoker
    Then the request is rejected with status 400

  Scenario: A blank command string is rejected
    When a blank command is invoked on the lot
    Then the request is rejected with status 400

  Scenario: A command undefined for the target type is refused
    When the command "updateSlotState" with new state "OCCUPIED" is invoked on the lot
    Then the request is rejected with status 400
