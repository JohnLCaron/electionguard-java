/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import ucar.ui.prefs.Debug;
import ucar.ui.widget.BAMutil;
import ucar.util.prefs.PreferencesExt;
import ucar.util.prefs.XMLStore;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/** ElectionRecord Viewer main program. */
public class ElectionGuardViewer extends JPanel {
  public static final String FRAME_SIZE = "FrameSize";

  private static JFrame frame;
  private static PreferencesExt prefs;
  private static XMLStore store;
  private static ElectionGuardViewer ui;
  private static boolean done;

  private final ElectionRecordPanel electionRecordPanel;
  private final InputBallotPanel inputBallotPanel;
  private final TrusteesPanel trusteesPanel;

  public ElectionGuardViewer(PreferencesExt prefs) {
    // the top UI
    JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    electionRecordPanel = new ElectionRecordPanel((PreferencesExt) prefs.node("ElectionRecordPanel"), frame);
    inputBallotPanel = new InputBallotPanel((PreferencesExt) prefs.node("InputBallotPanel"), frame);
    trusteesPanel = new TrusteesPanel((PreferencesExt) prefs.node("TrusteesPanel"), frame);

    tabbedPane.addTab("ElectionRecord", electionRecordPanel);
    tabbedPane.addTab("InputBallots", inputBallotPanel);
    tabbedPane.addTab("Trustees", trusteesPanel);
    tabbedPane.setSelectedIndex(0);

    setLayout(new BorderLayout());
    add(tabbedPane, BorderLayout.CENTER);
  }

  public void exit() {
    electionRecordPanel.save();
    inputBallotPanel.save();
    trusteesPanel.save();

    Rectangle bounds = frame.getBounds();
    prefs.putBeanObject(FRAME_SIZE, bounds);
    try {
      store.save();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }

    done = true; // on some systems, still get a window close event
    System.exit(0);
  }

  //////////////////////////////////////////////

  public static void main(String[] args) {

    // prefs storage
    try {
      String prefStore = XMLStore.makeStandardFilename(".electionguardjava", "ElectionRecordViewer.xml");
      store = XMLStore.createFromFile(prefStore, null);
      prefs = store.getPreferences();
      Debug.setStore(prefs.node("Debug"));
    } catch (IOException e) {
      System.out.println("XMLStore Creation failed " + e);
    }

    // put UI in a JFrame
    frame = new JFrame("ElectionRecord Viewer");
    ui = new ElectionGuardViewer(prefs);

    frame.setIconImage(BAMutil.getImage("electionguard-logo.png"));
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        if (!done) {
          ui.exit();
        }
      }
    });

    frame.getContentPane().add(ui);
    Rectangle bounds = (Rectangle) prefs.getBean(FRAME_SIZE, new Rectangle(50, 50, 800, 450));
    frame.setBounds(bounds);

    frame.pack();
    frame.setBounds(bounds);
    frame.setVisible(true);
  }
}
