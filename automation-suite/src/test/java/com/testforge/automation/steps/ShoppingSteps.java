package com.testforge.automation.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.testforge.automation.support.Browser;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.Select;

public class ShoppingSteps {

    @Given("the user is logged in as {string}")
    public void loggedInAs(String username) {
        Browser.open(Browser.BASE_URL);
        Browser.type(By.id("user-name"), username);
        Browser.type(By.id("password"), "secret_sauce");
        Browser.click(By.id("login-button"));
        assertEquals("Products", Browser.textOf(By.cssSelector(".title")));
    }

    @When("the user adds the {string} to the cart")
    public void addToCart(String product) {
        Browser.click(By.id("add-to-cart-" + product.toLowerCase(Locale.ROOT).replace(' ', '-')));
    }

    @Then("the cart badge shows {string}")
    public void cartBadgeShows(String expected) {
        assertEquals(expected, Browser.textOf(By.cssSelector(".shopping_cart_badge")));
    }

    @When("the user sorts the products by {string}")
    public void sortProductsBy(String option) {
        new Select(Browser.waitVisible(By.cssSelector(".product_sort_container"))).selectByVisibleText(option);
    }

    @Then("the product prices are in ascending order")
    public void pricesAreAscending() {
        List<BigDecimal> prices = Browser.textsOf(By.cssSelector(".inventory_item_price")).stream()
                .map(text -> new BigDecimal(text.replace("$", "").trim()))
                .toList();
        assertEquals(prices.stream().sorted().toList(), prices);
    }
}