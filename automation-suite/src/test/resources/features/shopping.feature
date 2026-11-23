Feature: Shopping

  @TC-10
  Scenario: Add a product to the cart
    Given the user is logged in as "standard_user"
    When the user adds the "Sauce Labs Backpack" to the cart
    Then the cart badge shows "1"

  @TC-11
  Scenario: Sort products by price low to high
    Given the user is logged in as "standard_user"
    When the user sorts the products by "Price (low to high)"
    Then the product prices are in ascending order

  # Intentionally wrong expectation: demonstrates how a failure is reported back to TestForge
  @TC-12
  Scenario: Missing password shows a specific error
    Given the user opens the Sauce Demo login page
    When the user logs in with username "standard_user" and no password
    Then the error message "Epic sadface: Password is mandatory" is displayed
