Feature: User accounts and sign-in
  Drivers may sign themselves up as END_USER accounts, while ADMIN and
  OPERATOR accounts can only be created by an existing administrator.

  Scenario: A driver signs up and logs in
    When a visitor signs up with email "driver@bdd.org", password "Drive@1" and role "END_USER"
    Then the request succeeds
    And the user "driver@bdd.org" can log in with password "Drive@1"
    And the logged-in user has role "END_USER"

  Scenario: Sign-up with a weak password is rejected
    When a visitor signs up with email "weak@bdd.org", password "abc" and role "END_USER"
    Then the request is rejected with status 400

  Scenario: Sign-up without a username is rejected
    When a visitor signs up with email "nouser@bdd.org" and password "Pass@1" but no username
    Then the request is rejected with status 400

  Scenario: Sign-up with a blank avatar is rejected
    When a visitor signs up with email "blank@bdd.org" and password "Pass@1" but a blank avatar
    Then the request is rejected with status 400

  Scenario: Logging in with a wrong password is rejected
    Given an administrator "admin@bdd.org" with password "Admin@1" exists
    When the user "admin@bdd.org" logs in with password "wrong"
    Then the request is rejected with status 401

  Scenario: A visitor cannot create an administrator account
    When a visitor signs up with email "rogue@bdd.org", password "Rogue@1" and role "ADMIN"
    Then the request is rejected with status 401

  Scenario: An administrator creates another administrator
    Given an administrator "admin@bdd.org" with password "Admin@1" exists
    When "admin@bdd.org" creates a user "second-admin@bdd.org" with role "ADMIN" and password "Second@1"
    Then the request succeeds
    And the user "second-admin@bdd.org" can log in with password "Second@1"
    And the logged-in user has role "ADMIN"

  Scenario: An end user cannot create an operator account
    Given an end user "driver@bdd.org" with password "Drive@1" exists
    When "driver@bdd.org" creates a user "op@bdd.org" with role "OPERATOR" and password "Oper@1"
    Then the request is rejected with status 403
