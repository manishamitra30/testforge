Feature: Login

  @TC-7
  Scenario: Login with valid credentials
    Given the user opens the Sauce Demo login page
    When the user logs in with username "standard_user" and password "secret_sauce"
    Then the products page is displayed

  @TC-8
  Scenario: Locked out user cannot log in
    Given the user opens the Sauce Demo login page
    When the user logs in with username "locked_out_user" and password "secret_sauce"
    Then the error message "Epic sadface: Sorry, this user has been locked out." is displayed

  @TC-9
  Scenario: Wrong password is rejected
    Given the user opens the Sauce Demo login page
    When the user logs in with username "standard_user" and password "wrong_password"
    Then the error message "Epic sadface: Username and password do not match any user in this service" is displayed
