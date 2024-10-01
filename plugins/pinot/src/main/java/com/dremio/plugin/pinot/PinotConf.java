package com.example.plugin.pinot;

import javax.validation.constraints.NotBlank;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Configuration for Pinot sources.
 */
public class PinotConf {
  private static final String DRIVER = "org.apache.pinot.client.PinotDriver";

  @NotBlank
  private String brokerHost;

  @NotBlank
  private String brokerPort;

  private int fetchSize = 200;

  public PinotConf(String brokerHost, String brokerPort, int fetchSize) {
    this.brokerHost = brokerHost;
    this.brokerPort = brokerPort;
    this.fetchSize = fetchSize;
  }

  public Connection getConnection() throws SQLException {
    // Register the Pinot JDBC driver
    try {
      Class.forName(DRIVER);
    } catch (ClassNotFoundException e) {
      throw new SQLException("Pinot JDBC Driver not found.", e);
    }

    String connectionString = String.format("jdbc:pinot://%s:%s", brokerHost, brokerPort);
    return DriverManager.getConnection(connectionString);
  }

  public int getFetchSize() {
    return fetchSize;
  }
}
