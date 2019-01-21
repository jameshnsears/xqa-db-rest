package xqa.integration.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jameshnsears.configuration.ConfigurationParameterResolver;
import com.github.jameshnsears.docker.DockerClient;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xqa.XqaQueryBalancerApplication;
import xqa.XqaQueryBalancerConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

@ExtendWith(ConfigurationParameterResolver.class)
public class DatabaseFixture extends Containerisation {
    protected static final DropwizardTestSupport<XqaQueryBalancerConfiguration> application = new DropwizardTestSupport<>(
            XqaQueryBalancerApplication.class,
            ResourceHelpers.resourceFilePath("xqa-query-balancer.yml"));
    protected static final ObjectMapper objectMapper = Jackson.newObjectMapper();
    protected static Logger logger = LoggerFactory.getLogger(DatabaseFixture.class);
    protected static DockerClient dockerClient;

    private String getResource() {
        return Thread.currentThread().getContextClassLoader().getResource("database").getPath();
    }

    protected void storagePopulate() throws SQLException, ClassNotFoundException, IOException {
        Connection connection = getConnection();
        populate(connection);
        connection.close();
    }

    private Connection getConnection() throws ClassNotFoundException, SQLException {
        Class.forName(application.getConfiguration().getDataSourceFactory().getDriverClass());
        return DriverManager.getConnection(
                application.getConfiguration().getDataSourceFactory().getUrl(),
                application.getConfiguration().getDataSourceFactory().getUser(),
                application.getConfiguration().getDataSourceFactory().getPassword());
    }

    protected void storageEmpty() throws SQLException, ClassNotFoundException {
        Connection connection = getConnection();
        truncate(connection);
        connection.close();
    }

    private void populate(Connection connection) throws IOException {
        try (Stream<Path> filePathStream = Files.walk(Paths.get(getResource()))) {
            filePathStream.forEach(filePath -> {
                if (Files.isRegularFile(filePath)) {
                    try {
                        insertFileContentsIntoDatabase(connection, filePath);
                    } catch (Exception exception) {
                        logger.error(exception.getMessage());
                    }
                }
            });
        }
    }

    private void truncate(Connection connection) {
        try {
            executeSql(connection, "truncate events;");
        } catch (SQLException exception) {
            logger.error(exception.getMessage());
        }
    }

    private void executeSql(Connection connection, String sql) throws SQLException {
        Statement statement;
        statement = connection.createStatement();
        statement.execute(sql);
        statement.close();
    }

    private void insertFileContentsIntoDatabase(Connection connection, Path filePath)
            throws Exception {
        logger.debug(filePath.toString());

        try (Stream<String> stream = Files.lines(filePath)) {
            stream.forEach(line -> {
                try {
                    executeSql(connection, line);
                } catch (SQLException exception) {
                    logger.error(exception.getMessage());
                }
            });
        }
    }
}
