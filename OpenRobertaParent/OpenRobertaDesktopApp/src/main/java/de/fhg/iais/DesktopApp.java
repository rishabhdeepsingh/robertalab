package de.fhg.iais;

import org.panda_lang.pandomium.Pandomium;
import org.panda_lang.pandomium.settings.PandomiumSettings;
import org.panda_lang.pandomium.wrapper.PandomiumBrowser;
import org.panda_lang.pandomium.wrapper.PandomiumClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class DesktopApp {
    public static void main(String[] args) {
        PandomiumSettings settings = PandomiumSettings.getDefaultSettings();

        Pandomium pandomium = new Pandomium(settings);
        pandomium.initialize();

        PandomiumClient client = pandomium.createClient();
        PandomiumBrowser browser = client.loadURL("https://google.com/");

        JFrame frame = new JFrame();
        frame.getContentPane().add(browser.toAWTComponent(), BorderLayout.CENTER);

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                pandomium.dispose();
                frame.dispose();
            }
        });

        frame.setTitle("OpenRobertaDesktopApp");
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setUndecorated(true);
        frame.setVisible(true);
    }
}
