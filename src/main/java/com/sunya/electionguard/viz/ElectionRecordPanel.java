package com.sunya.electionguard.viz;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.publish.CloseableIterableAdapter;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.verifier.ElectionRecord;
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

  ElectionDescriptionTable electionDescriptionTable;
  AcceptedBallotsTable acceptedBallotsTable;
  PlaintextTallyTable electionTallyTable;
  CiphertextTallyTable ciphertextTallyTable;
  PlaintextTallyTable spoiledTallyTable;
  PlaintextBallotsTable spoiledBallotsTable;

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
    BAMutil.setActionProperties(infoAction, "Information", "info on current Election Record", false, 'I', -1);
    BAMutil.addActionToContainer(buttPanel, infoAction);

    // components
    this.electionDescriptionTable = new ElectionDescriptionTable((PreferencesExt) prefs.node("ElectionDescription"))
            .addActions(buttPanel);
    this.acceptedBallotsTable = new AcceptedBallotsTable((PreferencesExt) prefs.node("AcceptedBallots"));
    this.ciphertextTallyTable = new CiphertextTallyTable((PreferencesExt) prefs.node("CiphertextTally"));
    this.electionTallyTable = new PlaintextTallyTable((PreferencesExt) prefs.node("ElectionTally"));
    this.spoiledTallyTable = new PlaintextTallyTable((PreferencesExt) prefs.node("SpoiledTallies"));
    this.spoiledBallotsTable = new PlaintextBallotsTable((PreferencesExt) prefs.node("SpoiledBallots"));

    // layout
    this.topPanel = new JPanel(new BorderLayout());
    this.topPanel.add(new JLabel("dir:"), BorderLayout.WEST);
    this.topPanel.add(electionRecordDirCB, BorderLayout.CENTER);
    this.topPanel.add(buttPanel, BorderLayout.EAST);
    setLayout(new BorderLayout());
    add(topPanel, BorderLayout.NORTH);

    JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    tabbedPane.addTab("ElectionDescription", this.electionDescriptionTable);
    tabbedPane.addTab("AcceptedBallots", this.acceptedBallotsTable);
    tabbedPane.addTab("CiphertextTally", this.ciphertextTallyTable);
    tabbedPane.addTab("ElectionTally", this.electionTallyTable);
    tabbedPane.addTab("SpoiledTallies", this.spoiledTallyTable);
    tabbedPane.addTab("SpoiledBallots", this.spoiledBallotsTable);
    tabbedPane.setSelectedIndex(0);
    add(tabbedPane, BorderLayout.CENTER);
  }

  boolean setElectionRecord(String electionRecord) {
    try {
      this.consumer = new Consumer(electionRecord);
      this.record =  consumer.readElectionRecord();
      electionDescriptionTable.setElectionDescription(record.election);

      if (record.acceptedBallots != null) {
        acceptedBallotsTable.setAcceptedBallots(record.acceptedBallots);
      }
      if (record.encryptedTally != null) {
        ciphertextTallyTable.setCiphertextTally(record.encryptedTally);
      }
      if (record.decryptedTally != null) {
        electionTallyTable.setPlaintextTallies(CloseableIterableAdapter.wrap(ImmutableList.of(record.decryptedTally)));
      }
      if (record.spoiledTallies != null) {
        spoiledTallyTable.setPlaintextTallies(record.spoiledTallies);
      }
      if (record.spoiledBallots != null) {
        spoiledBallotsTable.setBallots(record.spoiledBallots);
      }
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  //   public final String election_scope_id;
  //  public final ElectionType type;
  //  public final OffsetDateTime start_date;
  //  public final OffsetDateTime end_date;
  //  public final ImmutableList<GeopoliticalUnit> geopolitical_units;
  //  public final ImmutableList<Party> parties;
  //  public final ImmutableList<Candidate> candidates;
  //  public final ImmutableList<ContestDescription> contests;
  //  public final ImmutableList<BallotStyle> ballot_styles;
  //  public final Optional<InternationalizedText> name;
  //  public final Optional<ContactInformation> contact_information;
  //  public final Group.ElementModQ crypto_hash;
  void showInfo(Formatter f) {
    f.format("Election %s%n", this.electionRecordDir);
    if (this.record != null) {
      Election election = record.election;
      f.format("  election_scope_id = %s%n", election.election_scope_id);
      f.format("  type = %s%n", election.type);
      f.format("  name = %s%n", election.name);
      f.format("  start_date = %s%n", election.start_date);
      f.format("  end_date = %s%n", election.end_date);
      f.format("  description crypto hash = %s%n", election.crypto_hash);

      CiphertextElectionContext context = record.context;
      f.format("%nContext%n");
      f.format("  number_of_guardians = %s%n", context.number_of_guardians);
      f.format("  quorum = %s%n", context.quorum);
      f.format("  election public key = %s%n", context.elgamal_public_key.toShortString());
      f.format("  description hash = %s%n", context.description_hash);
      f.format("  election base hash = %s%n", context.crypto_base_hash);
      f.format("  extended base hash = %s%n", context.crypto_extended_base_hash);

      f.format("%n  Guardian Coefficient Validation Sets%n");
      for (KeyCeremony.CoefficientValidationSet coeff : record.guardianCoefficients) {
        f.format("    %s%n", coeff.owner_id());
      }
    }
  }

  void save() {
    fileChooser.save();
    electionRecordDirCB.save();

    electionDescriptionTable.save();
    acceptedBallotsTable.save();
    electionTallyTable.save();
    ciphertextTallyTable.save();
    spoiledTallyTable.save();
    spoiledBallotsTable.save();

    if (infoWindow != null) {
      prefs.putBeanObject(ElectionGuardViewer.FRAME_SIZE, infoWindow.getBounds());
    }
  }
}
