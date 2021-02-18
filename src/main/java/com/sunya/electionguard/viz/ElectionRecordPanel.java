package com.sunya.electionguard.viz;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Election;
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
  PreferencesExt prefs;
  TextHistoryPane ta;
  IndependentWindow infoWindow;
  ComboBox<String> electionRecordDirCB;
  JPanel topPanel;
  JPanel buttPanel = new JPanel();
  FileManager fileChooser;
  private final AbstractButton isProtoButt;
  boolean isProto;
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
        String dirName = fileChooser.chooseDirectory("/home/snake/tmp/electionguard/");
        if (dirName != null) {
          electionRecordDirCB.setSelectedItem(dirName);
        }
      }
    };
    BAMutil.setActionProperties(fileAction, "FileChooser", "open Local dataset...", false, 'L', -1);
    BAMutil.addActionToContainer(buttPanel, fileAction);

    // LOOK get rid of this
    AbstractAction coordAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        isProto = (Boolean) getValue(BAMutil.STATE);
        isProtoButt.setToolTipText(isProto ? "use Proto is ON" : "use Proto is OFF");
      }
    };
    this.isProto = prefs.getBoolean("coordState", false);
    String tooltip = isProto ? "use Proto is ON" : "use Proto is OFF";
    BAMutil.setActionProperties(coordAction, "V3", tooltip, true, 'C', -1);
    coordAction.putValue(BAMutil.STATE, isProto);
    this.isProtoButt = BAMutil.addActionToContainer(buttPanel, coordAction);

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
    this.electionDescriptionTable = new ElectionDescriptionTable((PreferencesExt) prefs.node("ElectionDescription"));
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
      this.record =  isProto ? consumer.readElectionRecordProto() : consumer.readElectionRecordJson();
      electionDescriptionTable.setElectionDescription(record.election);
      acceptedBallotsTable.setAcceptedBallots(record.acceptedBallots);
      if (record.ciphertextTally != null) {
        ciphertextTallyTable.setCiphertextTally(record.ciphertextTally);
      }
      if (record.decryptedTally != null) {
        electionTallyTable.setPlaintextTallies(CloseableIterableAdapter.wrap(ImmutableList.of(record.decryptedTally)));
      }
      spoiledTallyTable.setPlaintextTallies(record.spoiledTallies);
      spoiledBallotsTable.setBallots(record.spoiledBallots);
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
    f.format("%s%n", this.electionRecordDir);
    if (this.record != null) {
      Election election = record.election;
      f.format("  election_scope_id = %s%n", election.election_scope_id);
      f.format("  type = %s%n", election.type);
      f.format("  name = %s%n", election.name);
      f.format("  start_date = %s%n", election.start_date);
      f.format("  end_date = %s%n", election.end_date);

      f.format("%n  BallotStyle    geopolitical     parties%n");
      for (Election.BallotStyle style : election.ballot_styles) {
        f.format("    %s %s %s%n", style.object_id, style.geopolitical_unit_ids, style.party_ids);
      }

      f.format("%n  GeopoliticalUnit    name     type  contact%n");
      for (Election.GeopoliticalUnit gpunit : election.geopolitical_units) {
        f.format("    %s %s %s%n      %s%n", gpunit.object_id, gpunit.name, gpunit.type,
                gpunit.contact_information.map(Object::toString).orElse(""));
      }

      f.format("%n  Contests    title     subtitle%n");
      for (Election.ContestDescription contest : election.contests) {
        f.format("    %s %s %s%n", contest.object_id, contest.ballot_title, contest.ballot_subtitle);
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
