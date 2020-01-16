//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS com.sparkjava:spark-core:2.9.1,com.fasterxml.jackson.core:jackson-databind:2.10.2

import spark.Spark;
import java.util.HashMap;
import java.util.Random;

public class sparkjava {

    public static void main(String args[]) {
        new sparkjava().run();
    }

    void run() {
        var books = new HashMap();

        var get = Spark.get;
        var post = Spark.post;
        var random = new Random();

        Spark.setPort(8080);
        Spark.staticFileLocation("/static");

        get("/hello", (req, res) -> {
            return "Hello Spark!";
        });

        get("/json", "application/json", (req, res) -> {
            var date = java.util.Calendar.getInstance().getTime();
            var obj = {"name":"Nasven.js", "date":"${date}"};
            return obj;
}, function(model) JSON.stringify(model));

// Below sample is based on the Books sample in README.md of SparkJava source code

// Creates a new book resource, will return the ID to the created resource
// author and title are sent as query parameters e.g. /books?author=Foo&title=Bar
post("/books", function(request, response) {
  var author = request.queryParams("author");
  var title = request.queryParams("title");
  var book = {"author":"${author}", "title":"${title}"};

  var id = random.nextInt(999999);
  books.put(id.toString(), book);

  response.status(201); // 201 Created
  return id;
});

// Gets all available book resources (id's)
get("/books", function(request, response) {
  var ids = "";
  for each (var id in books.keySet()) {
    ids += id + " ";
  }
  return ids;
});

// Gets the book resource for the provided id
get("/books/:id", function(request, response) {
  var book = books.get(request.params(":id"));
  if (book != null) {
    return JSON.stringify(book);
  } else {
    response.status(404); // 404 Not found
    return "Book not found";
  }
});
    }

}
