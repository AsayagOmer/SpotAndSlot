Feature: Per-operator parking lot management
  Parking lots, sections and slots may only be created by OPERATORs,
  and each lot is controlled exclusively by the operator who created it.
  Deleting a lot cascades to its sections and slots.

  Background:
    Given an operator "owner@bdd.org" with password "Owner@1" exists
    And an operator "other@bdd.org" with password "Other@1" exists

  Scenario: An operator builds a lot with a section and slots
    When "owner@bdd.org" creates a parking lot named "BDD Lot" in "Tel Aviv"
    And "owner@bdd.org" adds a section "Section A" to the lot
    And "owner@bdd.org" adds a "REGULAR" slot "A-1" with status "FREE" to the section
    And "owner@bdd.org" adds a "HANDICAP" slot "A-2" with status "OCCUPIED" to the section
    Then the lot has 1 section and 2 slots

  Scenario: A non-operator cannot create a parking lot
    Given an end user "driver@bdd.org" with password "Drive@1" exists
    When "driver@bdd.org" creates a parking lot named "Rogue Lot" in "Nowhere"
    Then the request is rejected with status 403

  Scenario: Creating an object with a blank status is rejected
    When "owner@bdd.org" creates a parking lot named "Bad Lot" with a blank status
    Then the request is rejected with status 400

  Scenario: Only the owning operator can rename a lot
    Given "owner@bdd.org" creates a parking lot named "BDD Lot" in "Tel Aviv"
    When "other@bdd.org" renames the lot to "Stolen Lot"
    Then the request is rejected with status 403
    When "owner@bdd.org" renames the lot to "Renamed Lot"
    Then the request succeeds
    And the lot is named "Renamed Lot"

  Scenario: An operator cannot add a section to another operator's lot
    Given "owner@bdd.org" creates a parking lot named "BDD Lot" in "Tel Aviv"
    When "other@bdd.org" adds a section "Hijacked Section" to the lot
    Then the request is rejected with status 403

  Scenario: Only the owning operator can delete a lot
    Given "owner@bdd.org" creates a parking lot named "BDD Lot" in "Tel Aviv"
    When "other@bdd.org" deletes the lot
    Then the request is rejected with status 403

  Scenario: Deleting a lot removes its sections and slots
    Given "owner@bdd.org" creates a parking lot named "BDD Lot" in "Tel Aviv"
    And "owner@bdd.org" adds a section "Section A" to the lot
    And "owner@bdd.org" adds a "REGULAR" slot "A-1" with status "FREE" to the section
    When "owner@bdd.org" deletes the lot
    Then the request succeeds
    And the lot no longer exists
    And the section no longer exists
    And the slot "A-1" no longer exists
