Feature: Sprint 4 custom object queries
  Objects can be searched by exact alias, alias pattern, type and status,
  with pagination; every query authenticates the caller's credentials.

  Background:
    Given an operator "owner@bdd.org" with password "Owner@1" exists
    And "owner@bdd.org" creates a parking lot named "BDD Lot" in "Tel Aviv"
    And "owner@bdd.org" adds a section "Section A" to the lot
    And "owner@bdd.org" adds a "REGULAR" slot "A-1" with status "FREE" to the section
    And "owner@bdd.org" adds a "REGULAR" slot "A-2" with status "OCCUPIED" to the section

  Scenario: Search objects by type
    When "owner@bdd.org" searches objects by type "PARKING_SLOT"
    Then the search returns 2 objects

  Scenario: Search objects by exact alias
    When "owner@bdd.org" searches objects by alias "A-1"
    Then the search returns 1 object

  Scenario: Search objects by alias pattern
    When "owner@bdd.org" searches objects by alias pattern "A-"
    Then the search returns 2 objects

  Scenario: Search objects by status
    When "owner@bdd.org" searches objects by status "FREE"
    Then the search returns 1 object

  Scenario: Search results are paginated
    When "owner@bdd.org" searches objects by type "PARKING_SLOT" with page size 1
    Then the search returns 1 object

  Scenario: Searching requires valid credentials
    When "owner@bdd.org" searches objects by type "PARKING_SLOT" with a wrong password
    Then the request is rejected with status 401
