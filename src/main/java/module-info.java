module hellofx {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.json;
    requires java.net.http;

    opens com.example to javafx.fxml;
    exports com.example;
}