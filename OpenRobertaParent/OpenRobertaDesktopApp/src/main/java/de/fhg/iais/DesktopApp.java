package de.fhg.iais;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import org.eclipse.jetty.server.Server;
import org.json.JSONObject;
import org.panda_lang.pandomium.Pandomium;
import org.panda_lang.pandomium.settings.PandomiumSettings;
import org.panda_lang.pandomium.wrapper.PandomiumBrowser;
import org.panda_lang.pandomium.wrapper.PandomiumClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhg.iais.roberta.main.ServerStarter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class DesktopApp {
    private static final Logger LOG = LoggerFactory.getLogger(DesktopApp.class);

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        serverSocket.close();
        String port = String.valueOf(serverSocket.getLocalPort());
        Boolean isSingleUser = false;
        updateFile(false);
        if ( args.length > 0 ) {
            isSingleUser = args[0].toLowerCase().equals("true");
            if ( isSingleUser ) {
                updateFile(true);
            }
        }
        String[] strings =
            new String[] {
                "-d",
                "database.mode=embedded",
                "-d",
                "database.parentdir=../OpenRobertaServer",
                "-d",
                "server.staticresources.dir=../OpenRobertaServer/staticResources",
                "-d",
                "server.port=" + port,
                "-d",
                "single.user=" + (isSingleUser ? "true" : "false")
            };
        OptionParser parser = new OptionParser();
        OptionSpec<String> defineOpt = parser.accepts("d").withRequiredArg().ofType(String.class);
        OptionSet options = parser.parse(strings);
        List<String> defines = defineOpt.values(options);

        final ServerStarter serverStarter = new ServerStarter(null, defines);
        Server server = serverStarter.start();
        PandomiumSettings settings = PandomiumSettings.getDefaultSettings();
        Pandomium pandomium = new Pandomium(settings);
        pandomium.initialize();

        PandomiumClient client = pandomium.createClient();
        PandomiumBrowser browser = client.loadURL("http://0.0.0.0:" + port);
            JFrame frame = new JFrame();
        frame.getContentPane().add(browser.toAWTComponent(), BorderLayout.CENTER);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                pandomium.dispose();
                frame.dispose();
            }
        });

        frame.setTitle("OpenRobertaDesktopApp");
        frame.setSize(1380, 760);
        frame.getAccessibleContext();
        frame.setVisible(true);
        server.join();
        System.exit(0);
    }

    private static void updateFile(Boolean val) throws Exception {
        Path currentRelativePath = Paths.get("").toAbsolutePath().getParent();
        Path openRobertaServerPath = currentRelativePath.resolve("OpenRobertaServer");
        Path staticResources = openRobertaServerPath.resolve("staticResources");
        File singleUserFile = new File(staticResources + "//single-user.json");
        Path singleUserPath = staticResources.resolve("single-user.json");
        JSONObject dataTrue = new JSONObject();
        if ( val ) {
            dataTrue.put("single-user", "true");
        } else {
            dataTrue.put("single-user", "false");
        }
        List<String> data = new ArrayList<>();
        data.add(dataTrue.toString());
        if ( singleUserFile.createNewFile() ) {
            Files.write(singleUserPath, data, Charset.defaultCharset());
        } else {
            singleUserFile.delete();
            singleUserFile.createNewFile();
            Files.write(singleUserPath, data, Charset.defaultCharset());
        }
    }
}