var GenericBeanDefinition = Packages.org.springframework.beans.factory.support.GenericBeanDefinition;
var MutablePropertyValues = Packages.org.springframework.beans.MutablePropertyValues;
var HashMap = Packages.java.util.HashMap;
var SimpleUrlHandlerMapping = Packages.org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
var SpringApplication = Packages.org.springframework.boot.SpringApplication;
var Controller = Packages.org.springframework.web.servlet.mvc.Controller;

var MainController = new Controller() {
  handleRequest: function(request, response) {
    new MappingJackson2HttpMessageConverter().write(RESPONSE, MediaType.APPLICATION_JSON, new ServletServerHttpResponse(response));
    return null;
  }
}

var BOOT_CLASSES = Java.to([
      MainController.getClass().class,
      Packages.org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
      Packages.org.springframework.boot.autoconfigure.MessageSourceAutoConfiguration.class,
      Packages.org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration.class,
      Packages.org.springframework.boot.autoconfigure.web.DispatcherServletAutoConfiguration.class,
      Packages.org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration.class,
      Packages.org.springframework.boot.autoconfigure.web.HttpMessageConvertersAutoConfiguration.class,
      Packages.org.springframework.boot.autoconfigure.web.MultipartAutoConfiguration.class,
      Packages.org.springframework.boot.autoconfigure.web.ServerPropertiesAutoConfiguration.class,
      Packages.org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration.class,
], "java.lang.Class[]");

app = new SpringApplication(BOOT_CLASSES);
app.addInitializers(function(applicationContext) {
      var definition = new GenericBeanDefinition();
      var values = new MutablePropertyValues();
      var urls = new HashMap();
      urls.put("/main*", "mainController");
      values.add("urlMap", urls);
      values.add("order", 0);
      definition.setPropertyValues(values);
      definition.setBeanClass(SimpleUrlHandlerMapping.class);
      var beanFactory = applicationContext.getBeanFactory();
      beanFactory.registerBeanDefinition("mainUrlMapping", definition);
    });
app.run();

daemon();
