package com.sunya.electionguard.viz;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.sunya.electionguard.ballot.DecryptingGuardian;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import com.sunya.electionguard.verifier.VerifyElectionRecord;
import electionguard.ballot.Guardian;
import ucar.ui.prefs.ComboBox;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.FileManager;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Formatter;

class ElectionRecordPanel extends JPanel {
  private final PreferencesExt prefs;
  private final JPanel buttPanel = new JPanel();

  TextHistoryPane ta;
  IndependentWindow infoWindow;
  ComboBox<String> electionRecordDirCB;
  JPanel topPanel;

  FileManager fileChooser;
  boolean eventOk = true;

  String electionRecordDir = "none";
  Consumer consumer;
  ElectionRecord record;

  ManifestTable manifestTable;
  SubmittedBallotsTable submittedBallotsTable;
  PlaintextTallyTable plaintextTallyTable;
  CiphertextTallyTable ciphertextTallyTable;
  PlaintextTallyTable spoiledBallotsTable;

  ElectionRecordPanel(PreferencesExt prefs, JFrame frame) {
    this.prefs = prefs;

    ////// Choose the electionRecordDir
    this.fileChooser = new FileManager(frame, null, null, (PreferencesExt) prefs.node("FileManager"));
    this.electionRecordDirCB = new ComboBox<>((PreferencesExt) prefs.node("electionRecordDirCB"));
    this.electionRecordDirCB.addChangeListener(e -> {
      if (!this.eventOk) {
        return;
      }
      this.electionRecordDir = (String) electionRecordDirCB.getSelectedItem();
      if (setElectionRecord(this.electionRecordDir)) {
        this.eventOk = false;
        this.electionRecordDirCB.addItem(this.electionRecordDir);
        this.eventOk = true;
      }
    });
    AbstractAction fileAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String dirName = fileChooser.chooseDirectory("");
        if (dirName != null) {
          electionRecordDirCB.setSelectedItem(dirName);
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
    BAMutil.setActionProperties(infoAction, "Information", "info on Election Record", false, 'I', -1);
    BAMutil.addActionToContainer(buttPanel, infoAction);

    AbstractAction verifyAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Formatter f = new Formatter();
        verify(f);
        ta.setText(f.toString());
        infoWindow.show();
      }
    };
    BAMutil.setActionProperties(verifyAction, "Dump", "Verify Election Record", false, 'V', -1);
    BAMutil.addActionToContainer(buttPanel, verifyAction);

    // components
    this.manifestTable = new ManifestTable((PreferencesExt) prefs.node("Manifest"))
            .addActions(buttPanel);
    this.submittedBallotsTable = new SubmittedBallotsTable((PreferencesExt) prefs.node("CastBallots"));
    this.ciphertextTallyTable = new CiphertextTallyTable((PreferencesExt) prefs.node("CiphertextTally"));
    this.plaintextTallyTable = new PlaintextTallyTable((PreferencesExt) prefs.node("PlaintextTally"));
    this.spoiledBallotsTable = new PlaintextTallyTable((PreferencesExt) prefs.node("SpoiledBallots"));

    // layout
    this.topPanel = new JPanel(new BorderLayout());
    this.topPanel.add(new JLabel("dir:"), BorderLayout.WEST);
    this.topPanel.add(electionRecordDirCB, BorderLayout.CENTER);
    this.topPanel.add(buttPanel, BorderLayout.EAST);
    setLayout(new BorderLayout());
    add(topPanel, BorderLayout.NORTH);

    JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    tabbedPane.addTab("Manifest", this.manifestTable);
    tabbedPane.addTab("SubmittedBallots", this.submittedBallotsTable);
    tabbedPane.addTab("CiphertextTally", this.ciphertextTallyTable);
    tabbedPane.addTab("ElectionTally", this.plaintextTallyTable);
    tabbedPane.addTab("SpoiledBallots", this.spoiledBallotsTable);
    tabbedPane.setSelectedIndex(0);
    add(tabbedPane, BorderLayout.CENTER);
  }

  boolean setElectionRecord(String electionRecordLocation) {
    try {
      this.consumer = new Consumer(electionRecordLocation);
      Formatter error = new Formatter();
      if (!this.consumer.isValidElectionRecord(error)) {
        JOptionPane.showMessageDialog(null, error.toString());
        return false;
      }
      this.record = consumer.readElectionRecord();
      manifestTable.setElectionManifest(record.manifest());

      submittedBallotsTable.setAcceptedBallots(record.manifest(), record, record.submittedBallots());
      if (record.ciphertextTally() != null) {
        ciphertextTallyTable.setCiphertextTally(record.ciphertextTally());
      }
      if (record.decryptedTally() != null) {
        plaintextTallyTable.setPlaintextTallies(record.manifest(), ImmutableList.of(record.decryptedTally()));
      }
      spoiledBallotsTable.setPlaintextTallies(record.manifest(), record.spoiledBallotTallies());
    } catch (Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(null, e.getMessage());
    }
    return true;
  }

  void showInfo(Formatter f) {
    f.format("Election Record %s%n", this.electionRecordDir);
    if (this.record != null) {
      f.format("  protoVersion = %s%n", record.protoVersion());
      Manifest manifest = record.manifest();
      f.format("%nManifest%n");
      f.format("  spec_version = %s%n", manifest.specVersion());
      f.format("  election_scope_id = %s%n", manifest.electionScopeId());
      f.format("  type = %s%n", manifest.electionType());
      f.format("  name = %s%n", manifest.name());
      f.format("  start_date = %s%n", manifest.startDate());
      f.format("  end_date = %s%n", manifest.endDate());
      f.format("  manifest crypto hash = %s (%s) %n", manifest.cryptoHash(), manifest.cryptoHash().getBigInt());

      ElectionConstants constants = record.constants();
      f.format("%nConstants%n");
      f.format("  name = %s%n", constants.name);
      f.format("  large_prime = %s%n", Group.int_to_p_unchecked(constants.largePrime).toShortString());
      f.format("  small_prime = %s%n", Group.int_to_q_unchecked(constants.smallPrime));
      f.format("  cofactor    = %s%n", Group.int_to_p_unchecked(constants.cofactor).toShortString());
      f.format("  generator   = %s%n", Group.int_to_p_unchecked(constants.generator).toShortString());

      f.format("%nContext%n");
      f.format("  number_of_guardians = %s%n", record.numberOfGuardians());
      f.format("  quorum = %s%n", record.quorum());
      if (record.electionPublicKey() != null) {
        f.format("  election public key = %s%n", record.electionPublicKey().toShortString());
      }
      f.format("  base hash = %s%n", record.baseHash());
      f.format("  extended base hash = %s%n", record.extendedHash());

      /*
      f.format("%n  EncryptionDevices%n");
      for (Encrypt.EncryptionDevice device : record.devices) {
        f.format("    %d session=%d launch=%d location=%s%n", device.deviceId(), device.sessionId(), device.launchCode(), device.location());
      } */

      f.format("%n  Guardian Records: id, sequence #commitments #proofs%n");
      for (Guardian gr : record.guardians()) {
        f.format("    %10s %10d %10d %10d%n", gr.getGuardianId(), gr.getXCoordinate(),
                gr.getCoefficientCommitments().size(), gr.getCoefficientProofs().size());
      }

      f.format("%n  Available Guardians    lagrange%n");
      for (DecryptingGuardian guardian : record.availableGuardians()) {
        f.format("    %10s %10d %10s%n", guardian.guardianId(), guardian.xCoordinate(), guardian.lagrangeCoefficient());
      }

      f.format("%nAcceptedBallots %d%n", Iterables.size(record.submittedBallots()));
      f.format("SpoiledBallots %d%n", Iterables.size(record.spoiledBallotTallies()));
      f.format("EncryptedTally present = %s%n", record.ciphertextTally() != null);
      f.format("DecryptedTally present = %s%n", record.decryptedTally() != null);
    }
  }

  void verify(Formatter f) {
    if (record == null) {
      return;
    }
    f.format(" Verify ElectionRecord from %s%n", this.consumer.path.toString());
    boolean ok = VerifyElectionRecord.verifyElectionRecord(this.record, false);
    f.format(" OK =  %s%n", ok);
  }


  void save() {
    fileChooser.save();
    electionRecordDirCB.save();

    manifestTable.save();
    submittedBallotsTable.save();
    plaintextTallyTable.save();
    ciphertextTallyTable.save();
    spoiledBallotsTable.save();

    if (infoWindow != null) {
      prefs.putBeanObject(ElectionGuardViewer.FRAME_SIZE, infoWindow.getBounds());
    }
  }
}
