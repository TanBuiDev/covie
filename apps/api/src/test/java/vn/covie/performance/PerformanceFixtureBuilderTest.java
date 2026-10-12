package vn.covie.performance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PerformanceFixtureBuilderTest {
    @Test void pilotFixtureAllowListMatchesActualMigrationTables() throws Exception {
        assertThatCode(()->PerformanceFixtureBuilder.assertPilotScale(connection(1_000_000L)))
                .doesNotThrowAnyException();
    }

    @Test void smallDatabaseCannotPassThePilotScaleGate() throws Exception {
        assertThatThrownBy(()->PerformanceFixtureBuilder.assertPilotScale(connection(0L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fixture too small");
    }

    private Connection connection(long rows) throws Exception {
        var tables=new HashSet<String>();
        var tablePattern=Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z_]+)");
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))) {
            for(var file:files.filter(path->path.toString().endsWith(".sql")).toList()) {
                var matcher=tablePattern.matcher(Files.readString(file));
                while(matcher.find()) tables.add(matcher.group(1));
            }
        }
        Connection connection=mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation->{
            PreparedStatement query=mock(PreparedStatement.class);
            var table=new String[1];
            doAnswer(call->{table[0]=call.getArgument(1);return null;}).when(query).setString(eq(1),anyString());
            when(query.executeQuery()).thenAnswer(call->{
                ResultSet result=mock(ResultSet.class);
                when(result.next()).thenReturn(true);
                when(result.getBoolean(1)).thenReturn(tables.contains(table[0].replace("public.","")));
                return result;
            });
            return query;
        });
        Statement statement=mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenAnswer(call->{
            ResultSet result=mock(ResultSet.class);
            when(result.next()).thenReturn(true);
            when(result.getLong(1)).thenReturn(rows);
            return result;
        });
        return connection;
    }
}
