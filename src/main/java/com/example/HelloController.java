package com.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.function.Predicate;

public class HelloController implements Initializable {

    @FXML private ListView<ChatMessage> chatList;
    @FXML private TextField inputField;
    @FXML private TextField usernameField;
    @FXML private CheckBox hideMyMessagesCheck;

    private final HelloModel model = new HelloModel();
    private final ObservableList<ChatMessage> masterList = FXCollections.observableArrayList();
    private FilteredList<ChatMessage> filteredList;

    private String getCurrentUsername() {
        String u = usernameField.getText();
        if (u == null) return "Anonymous";
        u = u.trim();
        return u.isEmpty() ? "Anonymous" : u;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        filteredList = new FilteredList<>(masterList, msg -> true);
        chatList.setItems(filteredList);

        chatList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ChatMessage msg, boolean empty) {
                super.updateItem(msg, empty);
                if (empty || msg == null) { setGraphic(null); return; }

                Text user = new Text(msg.getUsername());
                user.setStyle("-fx-font-weight: bold;");
                Text time = new Text(" (" + msg.getTimestamp() + ")\n");
                time.setStyle("-fx-fill: gray; -fx-font-size: 12px;");

                if (msg.getFileName() != null && msg.getFileUrl() != null) {
                    if (msg.getMimeType() != null && msg.getMimeType().startsWith("image/")) {
                        try {
                            ImageView imageView = new ImageView(new Image(msg.getFileUrl(), true));
                            imageView.setFitWidth(200);
                            imageView.setPreserveRatio(true);
                            Text messageText = new Text(msg.getMessage() + "\n");
                            messageText.setStyle("-fx-font-size: 14px;");
                            setGraphic(new TextFlow(user, time, messageText, imageView));
                        } catch (Exception e) { e.printStackTrace(); }
                    } else {
                        Hyperlink link = new Hyperlink(msg.getFileName());
                        final String fileUrl = msg.getFileUrl();
                        link.setOnAction(ev -> HelloFX.hostServices().showDocument(fileUrl));
                        Text messageText = new Text(msg.getMessage() + "\n");
                        messageText.setStyle("-fx-font-size: 14px;");
                        setGraphic(new TextFlow(user, time, messageText, link));
                    }
                } else {
                    Text text = new Text(msg.getMessage());
                    text.setStyle("-fx-font-size: 14px;");
                    setGraphic(new TextFlow(user, time, text));
                }
            }
        });

        hideMyMessagesCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updateFilterPredicate());
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> updateFilterPredicate());

        model.loadHistory(msg -> Platform.runLater(() -> masterList.add(msg)));
        model.listenForMessages(msg -> Platform.runLater(() -> masterList.add(msg)));
    }

    private void updateFilterPredicate() {
        final String current = getCurrentUsername();
        final boolean hideMine = hideMyMessagesCheck.isSelected();
        Predicate<ChatMessage> pred = msg -> !hideMine || !current.equals(msg.getUsername());
        filteredList.setPredicate(pred);
    }

    @FXML
    private void onSend() {
        String user = getCurrentUsername();
        String msg = inputField.getText().trim();
        if (msg.isEmpty()) return;
        inputField.clear();

        new Thread(() -> {
            try { model.sendMessage(user, msg); }
            catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML
    private void onAttachFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a file to send");
        File file = fileChooser.showOpenDialog(chatList.getScene().getWindow());
        if (file != null) new Thread(() -> model.sendFile(getCurrentUsername(), file)).start();
    }
}
