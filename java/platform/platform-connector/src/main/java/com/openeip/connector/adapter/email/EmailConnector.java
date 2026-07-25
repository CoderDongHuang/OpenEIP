package com.openeip.connector.adapter.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.shared.ConnectorAdapterException;
import com.openeip.connector.spi.ConfigField;
import com.openeip.connector.spi.ConfigField.FieldType;
import com.openeip.connector.spi.ConnectionTestResult;
import com.openeip.connector.spi.ConnectorConfig;
import com.openeip.connector.spi.ConnectorMetadata;
import com.openeip.connector.spi.ConnectorSpi;
import com.openeip.connector.spi.DataReader;
import com.openeip.connector.spi.DataWriter;
import com.openeip.connector.spi.MetadataSchema;
import com.openeip.connector.spi.MetadataSchema.ResourceSchema;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public class EmailConnector implements ConnectorSpi {
  private final ObjectMapper mapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The mapper is an application-scoped collaborator.")
  public EmailConnector(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ConnectorMetadata getMetadata() {
    return new ConnectorMetadata(
        ConnectorType.EMAIL, "Email", "1.0.0", "SMTP/IMAP email connector", true, true);
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    return List.of(
        field("smtpHost", "SMTP host", FieldType.TEXT, true, false, null),
        field("smtpPort", "SMTP port", FieldType.NUMBER, true, false, "587"),
        field("imapHost", "IMAP host", FieldType.TEXT, true, false, null),
        field("imapPort", "IMAP port", FieldType.NUMBER, true, false, "993"),
        field("fromAddress", "From address", FieldType.TEXT, true, false, null),
        field("ssl", "TLS", FieldType.BOOLEAN, false, false, "true"),
        field("username", "Username", FieldType.TEXT, true, true, null),
        field("password", "Password", FieldType.TEXT, true, true, null));
  }

  @Override
  public ConnectionTestResult testConnection(ConnectorConfig config) {
    long started = System.nanoTime();
    try (Transport transport = smtp(config)) {
      transport.connect(credential(config, "username"), credential(config, "password"));
      return ConnectionTestResult.success(elapsed(started));
    } catch (Exception exception) {
      return ConnectionTestResult.failure(
          elapsed(started), "CONN-EMAIL-CONNECTION", "Email server connection failed");
    }
  }

  @Override
  public MetadataSchema extractMetadata(ConnectorConfig config) {
    try (Store store = imap(config)) {
      Folder[] folders = store.getDefaultFolder().list();
      List<String> fields =
          java.util.Arrays.stream(folders).map(Folder::getFullName).limit(100).toList();
      return new MetadataSchema(List.of(new ResourceSchema("folders", "mailbox", fields)));
    } catch (Exception exception) {
      throw adapter("CONN-EMAIL-METADATA", "Email metadata extraction failed", exception, true);
    }
  }

  @Override
  public DataReader createReader(ConnectorConfig config) {
    return request -> {
      String folderName =
          request.resource().startsWith("folder:")
              ? request.resource().substring(7)
              : request.resource();
      if (!folderName.matches("[A-Za-z0-9 _./-]{1,100}")) {
        throw new ConnectorAdapterException("CONN-EMAIL-RESOURCE", "Invalid mail folder", false);
      }
      try (Store store = imap(config);
          Folder folder = store.getFolder(folderName)) {
        folder.open(Folder.READ_ONLY);
        int total = folder.getMessageCount();
        int start = Math.max(1, total - request.limit() + 1);
        Message[] messages = folder.getMessages(start, total);
        List<JsonNode> items = new ArrayList<>();
        for (Message message : messages) {
          ObjectNode item = mapper.createObjectNode();
          item.put("subject", message.getSubject());
          item.put("content", content(message));
          item.put(
              "sentAt",
              message.getSentDate() == null ? null : message.getSentDate().toInstant().toString());
          Address[] from = message.getFrom();
          if (from != null && from.length > 0) {
            item.put("from", from[0].toString());
          }
          items.add(item);
          if (items.size() >= request.limit()) {
            break;
          }
        }
        return new DataReader.ReadResult(items, null);
      } catch (ConnectorAdapterException exception) {
        throw exception;
      } catch (Exception exception) {
        throw adapter("CONN-EMAIL-READ", "Email read failed", exception, true);
      }
    };
  }

  @Override
  public Optional<DataWriter> createWriter(ConnectorConfig config) {
    return Optional.of(
        request -> {
          if (!"SEND".equalsIgnoreCase(request.operation()) || !request.data().isObject()) {
            throw new ConnectorAdapterException(
                "CONN-EMAIL-WRITE", "Only SEND with an object payload is allowed", false);
          }
          String to = request.data().path("to").asText("");
          if (!to.contains("@")) {
            throw new ConnectorAdapterException("CONN-EMAIL-WRITE", "Recipient is required", false);
          }
          try (Transport transport = smtp(config)) {
            Session session = session(config, true);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(required(config.values(), "fromAddress")));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true));
            message.setSubject(
                request.data().path("subject").asText("OpenEIP notification"), "UTF-8");
            message.setText(request.data().path("text").asText(""), "UTF-8");
            transport.connect(credential(config, "username"), credential(config, "password"));
            transport.sendMessage(message, message.getAllRecipients());
            return new DataWriter.WriteResult(to, "SENT");
          } catch (ConnectorAdapterException exception) {
            throw exception;
          } catch (Exception exception) {
            throw adapter("CONN-EMAIL-WRITE", "Email send failed", exception, true);
          }
        });
  }

  private Transport smtp(ConnectorConfig config) throws Exception {
    int port = port(config, "smtpPort", 587);
    return session(config, true)
        .getTransport(
            config.values().path("ssl").asBoolean(true) && port == 465 ? "smtps" : "smtp");
  }

  private Store imap(ConnectorConfig config) throws Exception {
    int port = port(config, "imapPort", 993);
    Store store =
        session(config, false)
            .getStore(
                config.values().path("ssl").asBoolean(true) && port == 993 ? "imaps" : "imap");
    store.connect(
        required(config.values(), "imapHost"),
        port,
        credential(config, "username"),
        credential(config, "password"));
    return store;
  }

  private Session session(ConnectorConfig config, boolean smtp) {
    Properties properties = new Properties();
    int port = port(config, smtp ? "smtpPort" : "imapPort", smtp ? 587 : 993);
    String protocol =
        smtp && config.values().path("ssl").asBoolean(true) && port == 465
            ? "smtps"
            : smtp
                ? "smtp"
                : config.values().path("ssl").asBoolean(true) && port == 993 ? "imaps" : "imap";
    String prefix = "mail." + protocol;
    properties.put(prefix + ".host", required(config.values(), smtp ? "smtpHost" : "imapHost"));
    properties.put(prefix + ".port", Integer.toString(port));
    properties.put(prefix + ".auth", "true");
    if (config.values().path("ssl").asBoolean(true) && (!smtp || port == 465 || port == 993)) {
      properties.put(prefix + ".ssl.enable", "true");
    } else {
      properties.put(prefix + ".starttls.enable", "true");
    }
    return Session.getInstance(properties, null);
  }

  private static String content(Message message) throws Exception {
    Object value = message.getContent();
    return value == null
        ? ""
        : value.toString().substring(0, Math.min(10000, value.toString().length()));
  }

  private static String credential(ConnectorConfig config, String name) {
    String value = config.credentials().get(name);
    if (value == null || value.isBlank()) {
      throw new ConnectorAdapterException(
          "CONN-CREDENTIAL", "Missing email credential: " + name, false);
    }
    return value;
  }

  private static String required(JsonNode config, String name) {
    String value = config.path(name).asText("").trim();
    if (value.isBlank()) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Missing email config: " + name, false);
    }
    return value;
  }

  private static int port(ConnectorConfig config, String name, int fallback) {
    int value = config.values().path(name).asInt(fallback);
    if (value < 1 || value > 65535) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Invalid email port", false);
    }
    return value;
  }

  private static ConfigField field(
      String name,
      String label,
      FieldType type,
      boolean required,
      boolean secret,
      String defaultValue) {
    return new ConfigField(name, label, type, required, secret, defaultValue, List.of());
  }

  private static long elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }

  private static ConnectorAdapterException adapter(
      String code, String message, Exception cause, boolean retryable) {
    ConnectorAdapterException result = new ConnectorAdapterException(code, message, retryable);
    result.initCause(cause);
    return result;
  }
}
