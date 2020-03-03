//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.github.bonigarcia:webdrivermanager:3.8.1
//DEPS org.seleniumhq.selenium:selenium-java:3.141.59
//DEPS org.slf4j:slf4j-simple:1.7.30

import static java.awt.Toolkit.getDefaultToolkit;
import static javax.imageio.ImageIO.write;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import io.github.bonigarcia.wdm.WebDriverManager;

public class webdriver {


  public static WebDriver setupDriver() {
    WebDriverManager.chromedriver().setup();
    ChromeOptions options = new ChromeOptions();
    options.addArguments("start-maximized"); 
    options.addArguments("enable-automation"); 
    options.addArguments("--no-sandbox"); 
    options.addArguments("--disable-infobars");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--disable-browser-side-navigation"); 
    options.addArguments("--disable-gpu");
    options.setPageLoadStrategy(PageLoadStrategy.NONE);
    WebDriver driver = new ChromeDriver(options);
    driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
    return driver;
  }

  public static void takeScreenShot() throws Exception {
    Robot robot = new Robot();
    Rectangle captureSize = new Rectangle(getDefaultToolkit().getScreenSize());
    BufferedImage capture = robot.createScreenCapture(captureSize);
    write(capture, "jpg", new File("full-screenshot.jpg"));
  }

  public static void googleIt(WebDriver driver, String search) throws Exception {
    driver.get("http://www.google.com/");
    //Thread.sleep(3000);  // Let the user actually see something!
    WebElement searchBox = driver.findElement(By.name("q"));
    searchBox.sendKeys(search);
    //Thread.sleep(1000);
    searchBox.submit();
    //Thread.sleep(3000);  // Let the user actually see something!
  }

  public static void main(String[] args) throws Exception {
    WebDriver driver = setupDriver();
    googleIt(driver, "j’bang");
    takeScreenShot();
    driver.quit();
  }
}
