package support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fake JDBC stack (Connection / Statement / ResultSet) implemented with dynamic
 * proxies, so the migration code can be exercised without a live MySQL server.
 *
 * <p>Records every SQL string passed to {@code execute} / {@code executeQuery}
 * and lets tests program the integer returned by single-column {@code COUNT}
 * style queries, as well as which statements should fail.
 */
public final class FakeJdbc {

    /** SQL passed to {@link Statement#execute(String)}, in order. */
    public final List<String> executed = new ArrayList<String>();
    /** SQL passed to {@link Statement#executeQuery(String)}, in order. */
    public final List<String> queried = new ArrayList<String>();

    /** Value returned by {@code rs.getInt(...)} for a given query (default 0). */
    public Function<String, Integer> intForQuery = sql -> 0;
    /** Statements whose {@code execute} should throw a SQLException. */
    public Predicate<String> failExecute = sql -> false;
    /** Error code reported by simulated failures (1146 = table not found). */
    public int failErrorCode = 1146;

    /** Commit / rollback / autoCommit bookkeeping. */
    public int commits = 0;
    public int rollbacks = 0;
    public Boolean lastAutoCommit = null;

    public Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionHandler());
    }

    private final class ConnectionHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method m, Object[] args) {
            switch (m.getName()) {
                case "createStatement":
                    return Proxy.newProxyInstance(
                            Statement.class.getClassLoader(),
                            new Class<?>[]{Statement.class},
                            new StatementHandler());
                case "setAutoCommit":
                    lastAutoCommit = (Boolean) args[0];
                    return null;
                case "commit":
                    commits++;
                    return null;
                case "rollback":
                    rollbacks++;
                    return null;
                case "isClosed":
                    return Boolean.FALSE;
                case "close":
                    return null;
                default:
                    return defaultFor(m.getReturnType());
            }
        }
    }

    private final class StatementHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws SQLException {
            switch (m.getName()) {
                case "execute": {
                    String sql = (String) args[0];
                    if (failExecute.test(sql)) {
                        throw new SQLException("simulated failure", "HY000", failErrorCode);
                    }
                    executed.add(sql);
                    return Boolean.FALSE;
                }
                case "executeQuery": {
                    String sql = (String) args[0];
                    queried.add(sql);
                    return Proxy.newProxyInstance(
                            ResultSet.class.getClassLoader(),
                            new Class<?>[]{ResultSet.class},
                            new ResultSetHandler(intForQuery.apply(sql)));
                }
                case "close":
                    return null;
                default:
                    return defaultFor(m.getReturnType());
            }
        }
    }

    /** Single-row result set: {@code next()} is true exactly once. */
    private final class ResultSetHandler implements InvocationHandler {
        private final int value;
        private boolean consumed = false;

        ResultSetHandler(int value) {
            this.value = value;
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) {
            switch (m.getName()) {
                case "next":
                    if (consumed) {
                        return Boolean.FALSE;
                    }
                    consumed = true;
                    return Boolean.TRUE;
                case "getInt":
                    return value;
                case "getString":
                    return String.valueOf(value);
                case "close":
                    return null;
                default:
                    return defaultFor(m.getReturnType());
            }
        }
    }

    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }
}
