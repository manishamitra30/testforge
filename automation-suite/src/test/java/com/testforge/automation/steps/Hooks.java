package com.testforge.automation.steps;

import com.testforge.automation.support.Browser;
import io.cucumber.java.After;
import io.cucumber.java.Before;

public class Hooks {

    @Before
    public void startBrowser() {
        Browser.start();
    }

    @After
    public void stopBrowser() {
        Browser.quit();
    }
}