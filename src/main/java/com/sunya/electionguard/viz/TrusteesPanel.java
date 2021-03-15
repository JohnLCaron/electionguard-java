package com.sunya.electionguard.viz;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.guardian.DecryptingTrustee;
import com.sunya.electionguard.proto.TrusteeFromProto;
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

class TrusteesPanel extends JPanel {
  final PreferencesExt prefs;
  final TextHistoryPane ta;
  final IndependentWindow infoWindow;
  final ComboBox<String> inputBallotDirCB;
  final JPanel topPanel;
  final JPanel buttPanel = new JPanel();
  final FileManager fileChooser;
  final TrusteeDecryptingTable trusteesDecryptingTable;

  boolean eventOk = true;
  String inputDir = "none";

  TrusteesPanel(PreferencesExt prefs, JFrame frame) {
    this.prefs = prefs;

    ////// Choose the inputBallotDir
    this.fileChooser = new FileManager(frame, null, null, (PreferencesExt) prefs.node("FileManager"));
    this.inputBallotDirCB = new ComboBox<>((PreferencesExt) prefs.node("inputDirCB"));
    this.inputBallotDirCB.addChangeListener(e -> {
      if (!this.eventOk) {
        return;
      }
      this.inputDir = (String) inputBallotDirCB.getSelectedItem();
      if (setInputBallots(this.inputDir)) {
        this.eventOk = false;
        this.inputBallotDirCB.addItem(this.inputDir);
        this.eventOk = true;
      }
    });
    AbstractAction fileAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String dirName = fileChooser.chooseFilename("");
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
    BAMutil.setActionProperties(infoAction, "Information", "info on current Manifest Record", false, 'I', -1);
    BAMutil.addActionToContainer(buttPanel, infoAction);

    // components
    this.trusteesDecryptingTable = new TrusteeDecryptingTable((PreferencesExt) prefs.node("DecryptingTrustees"));

    // layout
    this.topPanel = new JPanel(new BorderLayout());
    this.topPanel.add(new JLabel("file:"), BorderLayout.WEST);
    this.topPanel.add(inputBallotDirCB, BorderLayout.CENTER);
    this.topPanel.add(buttPanel, BorderLayout.EAST);
    setLayout(new BorderLayout());
    add(topPanel, BorderLayout.NORTH);

    JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    tabbedPane.addTab("DecryptingTrustees", this.trusteesDecryptingTable);
    tabbedPane.setSelectedIndex(0);
    add(tabbedPane, BorderLayout.CENTER);
  }

  boolean setInputBallots(String inputDir) {
    try {
      ImmutableList<DecryptingTrustee> trustees = TrusteeFromProto.readTrustees(inputDir);
      trusteesDecryptingTable.setTrustees(trustees);
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  void showInfo(Formatter f) {
    f.format("%s%n", this.inputDir);
  }

  void save() {
    fileChooser.save();
    inputBallotDirCB.save();
    trusteesDecryptingTable.save();
    if (infoWindow != null) {
      prefs.putBeanObject(ElectionGuardViewer.FRAME_SIZE, infoWindow.getBounds());
    }
  }
}
