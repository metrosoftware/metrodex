package metro.daemon;

import metro.BlockchainProcessor;
import metro.BlockchainProcessorImpl;
import metro.Metro;
import metro.util.Convert;
import metro.util.Logger;
import metro.util.ThreadPool;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class Daemon {
    public static final int DAEMON_PORT = 8135;
    private static final Server daemonServer;
    public static final int daemonServerIdleTimeout = 10000;

    static {
        boolean daemon = Metro.getBooleanProperty("metro.daemon");
        if (daemon) {
            daemonServer = new Server();
            ServerConnector connector;

            HttpConfiguration configuration = new HttpConfiguration();
            configuration.setSendDateHeader(false);
            configuration.setSendServerVersion(false);

            connector = new ServerConnector(daemonServer, new HttpConnectionFactory(configuration));
            connector.setPort(DAEMON_PORT);
            connector.setHost("127.0.0.1");
            connector.setIdleTimeout(daemonServerIdleTimeout);
            connector.setReuseAddress(true);
            daemonServer.addConnector(connector);
            Logger.logMessage("Daemon server using HTTP port " + DAEMON_PORT);

            ServletContextHandler apiHandler = new ServletContextHandler();
            ServletHolder daemonServletHolder = new ServletHolder(new DaemonServlet());
            daemonServletHolder.setInitParameter("dirAllowed", "false");
            daemonServletHolder.setInitParameter("welcomeServlets", "false");
            daemonServletHolder.setInitParameter("redirectWelcome", "false");
            daemonServletHolder.setInitParameter("gzip", "true");
            daemonServletHolder.setInitParameter("etags", "true");
            apiHandler.addServlet(daemonServletHolder, "/*");

            daemonServer.setHandler(apiHandler);
            daemonServer.setStopAtShutdown(true);
            ThreadPool.runBeforeStart(() -> {
                try {
                    daemonServer.start();
                    Logger.logMessage("Started Daemon server at " + "127.0.0.1" + ":" + DAEMON_PORT);
                } catch (Exception e) {
                    Logger.logErrorMessage("Failed to start API server", e);
                    throw new RuntimeException(e.toString(), e);
                }

            }, true);

            String blockNotifyCommand = Metro.getStringProperty("metro.daemon.blocknotify");
            if (blockNotifyCommand != null && blockNotifyCommand.trim().length() > 0) {
                BlockchainProcessorImpl.getInstance().addListener(block -> {
                    byte[] hash = block.getHash();
                    ArrayUtils.reverse(hash);
                    String command = blockNotifyCommand.replace("%s", Convert.toHexString(hash));
                    String output = executeCommand(command);
                    Logger.logInfoMessage("Block notify output:" + output);
                }, BlockchainProcessor.Event.AFTER_BLOCK_ACCEPT);
            }
        } else {
            daemonServer = null;
        }
    }

    public static void init() {
    }

    private static String executeCommand(String command) {

        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine())!= null) {
                output.append(line + "\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return output.toString();

    }

}
