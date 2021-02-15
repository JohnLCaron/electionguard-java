/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.sunya.electionguard.CiphertextAcceptedBallot;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterableAdapter;
import com.sunya.electionguard.publish.Consumer;
import ucar.ui.prefs.ComboBox;
import ucar.ui.prefs.Debug;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.FileManager;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;
import ucar.util.prefs.XMLStore;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Formatter;

/**
 * ElectionRecord Viewer
 */
public class ElectionRecordViewer extends JPanel {
  private static final String FRAME_SIZE = "FrameSize";

  private static JFrame frame;
  private static PreferencesExt prefs;
  private static XMLStore store;
  private static ElectionRecordViewer ui;
  private static boolean done;

  private final ElectionRecordPanel electionRecordPanel;
  FileManager fileChooser;
  Consumer consumer;
  private AbstractButton isProtoButt;
  boolean isProto;

  public ElectionRecordViewer(PreferencesExt prefs) {
    this.fileChooser = new FileManager(frame, null, null, (PreferencesExt) prefs.node("FileManager"));

    // the top UI
    JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    electionRecordPanel = new ElectionRecordPanel((PreferencesExt) prefs.node("ElectionRecordPanel"));
    tabbedPane.addTab("ElectionRecord", electionRecordPanel);
    tabbedPane.setSelectedIndex(0);

    setLayout(new BorderLayout());
    add(tabbedPane, BorderLayout.CENTER);
  }

  private class ElectionRecordPanel extends JPanel {
    PreferencesExt prefs;
    TextHistoryPane ta;
    IndependentWindow infoWindow;
    ComboBox<String> electionRecordDirCB;
    JPanel topPanel;
    JPanel buttPanel = new JPanel();
    AcceptedBallotsTable acceptedBallotsTable;
    boolean eventOk = true;

    ElectionRecordPanel(PreferencesExt prefs) {
      this.prefs = prefs;
      ta = new TextHistoryPane(true);
      infoWindow = new IndependentWindow("Details", BAMutil.getImage("electionguard-logo.png"), new JScrollPane(ta));
      Rectangle bounds = (Rectangle) prefs.getBean(FRAME_SIZE, new Rectangle(200, 50, 500, 700));
      infoWindow.setBounds(bounds);

      electionRecordDirCB = new ComboBox<>((PreferencesExt)prefs.node("electionRecordDirCB"));
      electionRecordDirCB.addChangeListener(e -> {
        if (!eventOk) {
          return;
        }
        String electionRecordDir = (String) electionRecordDirCB.getSelectedItem();
        if (setElectionRecord(electionRecordDir)) {
          eventOk = false;
          electionRecordDirCB.addItem(electionRecordDir);
          eventOk = true;
        }
      });

      topPanel = new JPanel(new BorderLayout());
      topPanel.add(new JLabel("dir:"), BorderLayout.WEST);
      topPanel.add(electionRecordDirCB, BorderLayout.CENTER);
      topPanel.add(buttPanel, BorderLayout.EAST);

      AbstractAction fileAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          String dirName = fileChooser.chooseDirectory("/home/snake/tmp/electionguard/");
          if (dirName != null) {
            electionRecordDirCB.setSelectedItem(dirName);
          }
        }
      };
      BAMutil.setActionProperties(fileAction, "FileChooser", "open Local dataset...", false, 'L', -1);
      BAMutil.addActionToContainer(buttPanel, fileAction);

      AbstractAction coordAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          isProto = (Boolean) getValue(BAMutil.STATE);
          isProtoButt.setToolTipText(isProto ? "use Proto is ON" : "use Proto is OFF");
        }
      };
      isProto = prefs.getBoolean("coordState", false);
      String tooltip = isProto ? "use Proto is ON" : "use Proto is OFF";
      BAMutil.setActionProperties(coordAction, "V3", tooltip, true, 'C', -1);
      coordAction.putValue(BAMutil.STATE, isProto);
      isProtoButt = BAMutil.addActionToContainer(buttPanel, coordAction);

      AbstractAction infoAction = new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          Formatter f = new Formatter();
          showInfo(f);
          ta.setText(f.toString());
          infoWindow.show();
        }
      };
      BAMutil.setActionProperties(infoAction, "Information", "info on current Election Record", false, 'I', -1);
      BAMutil.addActionToContainer(buttPanel, infoAction);

      setLayout(new BorderLayout());
      add(topPanel, BorderLayout.NORTH);

      acceptedBallotsTable = new AcceptedBallotsTable((PreferencesExt) prefs.node("ElectionRecord"), fileChooser);
      add(acceptedBallotsTable, BorderLayout.CENTER);
    }

    void setAcceptedBallots(CloseableIterable<CiphertextAcceptedBallot> acceptedBallots) {
      acceptedBallotsTable.setAcceptedBallots(acceptedBallots);
    }

    void showInfo(Formatter f) {
      acceptedBallotsTable.showInfo(f);
    }

    void save() {
      electionRecordDirCB.save();
      acceptedBallotsTable.save();
      if (infoWindow != null) {
        prefs.putBeanObject(FRAME_SIZE, infoWindow.getBounds());
      }
    }
  }

  boolean setElectionRecord(String electionRecord) {
    try {
      this.consumer =  new Consumer(electionRecord);
      if (isProto) {
        electionRecordPanel.setAcceptedBallots(consumer.acceptedBallotsProto());
      } else {
        electionRecordPanel.setAcceptedBallots(CloseableIterableAdapter.wrap(consumer.acceptedBallots()));
      }
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public void exit() {
    fileChooser.save();
    electionRecordPanel.save();

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
    ui = new ElectionRecordViewer(prefs);

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
