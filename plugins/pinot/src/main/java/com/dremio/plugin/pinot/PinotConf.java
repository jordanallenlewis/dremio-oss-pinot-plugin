package com.dremio.plugin.pinot;

import javax.validation.constraints.NotBlank;

import com.dremio.exec.catalog.conf.DisplayMetadata;
import com.dremio.exec.catalog.conf.NotMetadataImpacting;
import com.dremio.exec.catalog.conf.Secret;
import com.dremio.exec.catalog.conf.SourceType;
import com.dremio.exec.store.jdbc.CloseableDataSource;
import com.dremio.exec.store.jdbc.DataSources;
import com.dremio.exec.store.jdbc.JdbcPluginConfig;
import com.dremio.exec.store.jdbc.dialect.arp.ArpDialect;
import com.dremio.exec.store.jdbc.conf.AbstractArpConf;
import com.dremio.options.OptionManager;
import com.dremio.services.credentials.CredentialsService;
import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.protostuff.Tag;

import static com.google.common.base.Preconditions.checkNotNull;

@SourceType(value = "PINOT", label = "Pinot")
public class PinotConf extends AbstractArpConf<PinotConf> {
    private static final Logger logger = LoggerFactory.getLogger(PinotConf.class);

    private static final String ARP_FILENAME = "arp/implementation/pinot-arp.yaml";

    private static final ArpDialect createDialect() {
        logger.debug("Creating Pinot ARP Dialect");
        return AbstractArpConf.loadArpFile(ARP_FILENAME, (ArpDialect::new));
    }

    private static final ArpDialect ARP_DIALECT = createDialect();
    private static final String DRIVER = "org.apache.pinot.client.PinotDriver";

    @NotBlank
    @Tag(1)
    @DisplayMetadata(label = "JDBC URL")
    public String connectionString;

    @NotBlank
    @Tag(2)
    @DisplayMetadata(label = "Username")
    public String username;

    @NotBlank
    @Tag(3)
    @DisplayMetadata(label = "Password")
    @Secret
    public String password;

    @Tag(4)
    @DisplayMetadata(label = "Fetch Size")
    @NotMetadataImpacting
    public int fetchSize = 200;

    @Tag(5)
    @DisplayMetadata(label = "Maximum idle connections")
    @NotMetadataImpacting
    public int maxIdleConns = 8;

    @Tag(6)
    @DisplayMetadata(label = "Connection idle time (s)")
    @NotMetadataImpacting
    public int idleTimeSec = 60;

    @VisibleForTesting
    public String toJdbcConnectionString() {
        return this.connectionString;
    }

    @Override
    @VisibleForTesting
    public JdbcPluginConfig buildPluginConfig(
        JdbcPluginConfig.Builder configBuilder,
        CredentialsService credentialsService,
        OptionManager optionManager) {

        logger.debug("Building plugin config for Pinot");

        return configBuilder.withDialect(getDialect())
                            .withFetchSize(fetchSize)
                            .withDatasourceFactory(this::newDataSource)
                            .clearHiddenSchemas()
                            .addHiddenSchema("SYSTEM")
                            .build();
    }

    private CloseableDataSource newDataSource() {
        final String username = checkNotNull(this.username, "Missing username.");
        final String password = checkNotNull(this.password, "Missing password.");

        return DataSources.newGenericConnectionPoolDataSource(DRIVER,
            toJdbcConnectionString(), username, password, null,
            DataSources.CommitMode.DRIVER_SPECIFIED_COMMIT_MODE, maxIdleConns, idleTimeSec);
    }

    @Override
    public ArpDialect getDialect() {
        return ARP_DIALECT;
    }

    @VisibleForTesting
    public static ArpDialect getDialectSingleton() {
        return ARP_DIALECT;
    }
}
