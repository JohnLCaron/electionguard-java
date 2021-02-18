package com.sunya.electionguard.viz;

import com.sunya.electionguard.publish.CloseableIterableAdapter;
import com.sunya.electionguard.publish.Consumer;
import ucar.ui.prefs.ComboBox;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.FileManager;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Formatter;

class InputBallotPanel extends JPanel {
  final PreferencesExt prefs;
  final TextHistoryPane ta;
  final IndependentWindow infoWindow;
  final ComboBox<String> inputBallotDirCB;
  final JPanel topPanel;
  final JPanel buttPanel = new JPanel();
  final FileManager fileChooser;
  final  PlaintextBallotsTable inputBallotsTable;

  boolean eventOk = true;
  String inputBallotDir = "none";
  Consumer consumer;

  InputBallotPanel(PreferencesExt prefs, JFrame frame) {
    this.prefs = prefs;

    ////// Choose the electionRecordDir
    this.fileChooser = new FileManager(frame, null, null, (PreferencesExt) prefs.node("FileManager"));
    this.inputBallotDirCB = new ComboBox<>((PreferencesExt) prefs.node("inputBallotDirCB"));
    this.inputBallotDirCB.addChangeListener(e -> {
      if (!this.eventOk) {
        return;
      }
      this.inputBallotDir = (String) inputBallotDirCB.getSelectedItem();
      if (setInputBallots(this.inputBallotDir)) {
        this.eventOk = false;
        this.inputBallotDirCB.addItem(this.inputBallotDir);
        this.eventOk = true;
      }
    });
    AbstractAction fileAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String dirName = fileChooser.chooseFilename("/home/snake/dev/github/electionguard-java/test/resources/");
        if (dirName != null) {
          inputBallotDirCB.setSelectedItem(dirName);
        }
      }
    };
    BAMutil.setActionProperties(fileAction, "FileChooser", "open Local dataset...", false, 'L', -1);
    BAMutil.addActionToContainer(buttPanel, fileAction);

    // Popup info window
    this.ta = new TextHistoryPane(true);
    this.infoWindow = new IndependentWindow("Details", BAMutil.getImage("electionguard-logo.png"), new JScrollPane(ta));
    Rectangle bounds = (Rectangle) prefs.getBean(ElectionGuardViewer.FRAME_SIZE, new Rectangle(200, 50, 500, 700));
    this.infoWindow.setBounds(bounds);
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

    // components
    this.inputBallotsTable = new PlaintextBallotsTable((PreferencesExt) prefs.node("InputBallots"));

    // layout
    this.topPanel = new JPanel(new BorderLayout());
    this.topPanel.add(new JLabel("file:"), BorderLayout.WEST);
    this.topPanel.add(inputBallotDirCB, BorderLayout.CENTER);
    this.topPanel.add(buttPanel, BorderLayout.EAST);
    setLayout(new BorderLayout());
    add(topPanel, BorderLayout.NORTH);

    JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    tabbedPane.addTab("InputBallots", this.inputBallotsTable);
    tabbedPane.setSelectedIndex(0);
    add(tabbedPane, BorderLayout.CENTER);
  }

  boolean setInputBallots(String electionRecord) {
    try {
      this.consumer = new Consumer(electionRecord);
      inputBallotsTable.setBallots(CloseableIterableAdapter.wrap(consumer.inputBallots(inputBallotDir)));
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  void showInfo(Formatter f) {
    f.format("%s%n", this.inputBallotDir);
  }

  void save() {
    fileChooser.save();
    inputBallotDirCB.save();

    inputBallotsTable.save();

    if (infoWindow != null) {
      prefs.putBeanObject(ElectionGuardViewer.FRAME_SIZE, infoWindow.getBounds());
    }
  }
}
