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
        var random = new Random();

        Spark.setPort(8080);

        Spark.get("/hello", (req, res) -> {
            return "Hello Spark!";
        });

        Spark.get("/json", "application/json", (req, res) -> {
            var date = java.util.Calendar.getInstance().getTime();
            return String.format("{\"name\":\"jbang\", \"date\":\"%s\"}", date);
        });

        Spark.post("/books", (request, response) -> {
            var author = request.queryParams("author");
            var title = request.queryParams("title");
            var book = String.format("{\"author\":\"%s\", \"title\":\"%s\"}", author, title);

            var id = random.nextInt(999999);
            books.put(Integer.valueOf(id), book);

            response.status(201); // 201 Created
            return id;
        });

        // Gets all available book resources (id's)
        Spark.get("/books", (request, response) -> {
            var ids = "";
            for (var id : books.keySet()) {
                ids += id + " ";
            }
            return ids;
        });

        // Gets the book resource for the provided id
        Spark.get("/books/:id", (request, response) -> { 
            var book = books.get(request.params(":id"));
            if (book != null) {
                return book;
            } else {
                response.status(404); // 404 Not found
                return "Book not found";
            }
        });
    }

}
