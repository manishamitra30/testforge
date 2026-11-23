package com.testforge.automation.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.testforge.automation.support.Browser;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;

public class LoginSteps {

    @Given("the user opens the Sauce Demo login page")
    public void openLoginPage() {
        Browser.open(Browser.BASE_URL);
    }

    @When("the user logs in with username {string} and password {string}")
    public void logIn(String username, String password) {
        Browser.type(By.id("user-name"), username);
        Browser.type(By.id("password"), password);
        Browser.click(By.id("login-button"));
    }

    @When("the user logs in with username {string} and no password")
    public void logInWithoutPassword(String username) {
        Browser.type(By.id("user-name"), username);
        Browser.click(By.id("login-button"));
    }

    @Then("the products page is displayed")
    public void productsPageIsDisplayed() {
        assertEquals("Products", Browser.textOf(By.cssSelector(".title")));
    }

    @Then("the error message {string} is displayed")
    public void errorMessageIsDisplayed(String expected) {
        assertEquals(expected, Browser.textOf(By.cssSelector("[data-test='error']")));
    }
}