package io.github.borgautomation.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class InventoryPage extends BasePage {

    private final By inventoryContainer = By.id("inventory_container");
    private final By cartBadge = By.className("shopping_cart_badge");

    public InventoryPage(WebDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(inventoryContainer));
        return true;
    }

    public void addItemToCart(String addToCartButtonId) {
        By addToCartButton = By.id(addToCartButtonId);
        wait.until(ExpectedConditions.elementToBeClickable(addToCartButton)).click();
    }

    public int getCartBadgeCount() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(cartBadge));
        return Integer.parseInt(driver.findElement(cartBadge).getText());
    }
}
