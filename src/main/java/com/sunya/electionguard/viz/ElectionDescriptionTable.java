/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.input.ElectionInputValidation;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.stream.Collectors;

public class ElectionDescriptionTable extends JPanel {
  private final PreferencesExt prefs;

  private final BeanTable<BallotStyleBean> styleTable;
  private final BeanTable<ContestBean> contestTable;
  private final BeanTable<SelectionBean> selectionTable;
  private final BeanTable<PartyBean> partyTable;
  private final BeanTable<CandidateBean> candidateTable;
  private final BeanTable<GpUnitBean> gpunitTable;

  private final JSplitPane split1, split2, split3, split4, split5;
  private final  TextHistoryPane infoTA = new TextHistoryPane();
  private final IndependentWindow infoWindow;

  private Manifest election;

  public ElectionDescriptionTable(PreferencesExt prefs) {
    this.prefs = prefs;
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("electionguard-logo.png"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 100)));

    contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("ContestTable"), false,
            "Contest", "Manifest.ContestDescription", null);
    contestTable.addListSelectionListener(e -> {
      ContestBean contest = contestTable.getSelectedBean();
      if (contest != null) {
        setContest(contest);
      }
    });
    contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
            bean -> ((ContestBean)bean).contest.toString()));

    styleTable = new BeanTable<>(BallotStyleBean.class, (PreferencesExt) prefs.node("StyleTable"), false,
            "BallotStyle", "Manifest.BallotStyle", null);
    styleTable.addPopupOption("Show BallotStyle", styleTable.makeShowAction(infoTA, infoWindow,
            bean -> ((BallotStyleBean)bean).style.toString()));

    selectionTable = new BeanTable<>(SelectionBean.class, (PreferencesExt) prefs.node("SelectionTable"), false,
            "Selection", "Manifest.Selection", null);
    selectionTable.addPopupOption("Show Selection", selectionTable.makeShowAction(infoTA, infoWindow,
            bean -> ((SelectionBean)bean).selection.toString()));

    partyTable = new BeanTable<>(PartyBean.class, (PreferencesExt) prefs.node("PartyTable"), false,
            "Party", "Manifest.Party", null);
    partyTable.addPopupOption("Show Party", partyTable.makeShowAction(infoTA, infoWindow,
            bean -> ((PartyBean)bean).org.toString()));

    candidateTable = new BeanTable<>(CandidateBean.class, (PreferencesExt) prefs.node("CandidateTable"), false,
            "Candidate", "Manifest.Candidate", null);
    candidateTable.addPopupOption("Show Candidate", candidateTable.makeShowAction(infoTA, infoWindow,
            bean -> ((CandidateBean)bean).org.toString()));

    gpunitTable = new BeanTable<>(GpUnitBean.class, (PreferencesExt) prefs.node("GpUnitTable"), false,
            "GeopoliticalUnit", "Manifest.GeopoliticalUnit", null);
    gpunitTable.addPopupOption("Show GeopoliticalUnit", gpunitTable.makeShowAction(infoTA, infoWindow,
            bean -> ((GpUnitBean)bean).gpunit.toString()));

    // layout
    split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, candidateTable, partyTable);
    split1.setDividerLocation(prefs.getInt("splitPos1", 200));

    split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, gpunitTable);
    split2.setDividerLocation(prefs.getInt("splitPos2", 200));

    split3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split2, styleTable);
    split3.setDividerLocation(prefs.getInt("splitPos3", 200));

    split4 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split3, contestTable);
    split4.setDividerLocation(prefs.getInt("splitPos4", 200));

    split5 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split4, selectionTable);
    split5.setDividerLocation(prefs.getInt("splitPos5", 200));

    setLayout(new BorderLayout());
    add(split5, BorderLayout.CENTER);
  }

  ElectionDescriptionTable addActions(JPanel buttPanel) {
    AbstractAction valAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        validateElection();
      }
    };
    BAMutil.setActionProperties(valAction, "alien", "Validate Manifest", false, 'V', -1);
    BAMutil.addActionToContainer(buttPanel, valAction);
    return this;
  }

  void setElectionDescription(Manifest election) {
    this.election = election;
    candidateTable.setBeans(election.candidates.stream().map(CandidateBean::new).collect(Collectors.toList()));
    partyTable.setBeans(election.parties.stream().map(PartyBean::new).collect(Collectors.toList()));
    gpunitTable.setBeans(election.geopolitical_units.stream().map(GpUnitBean::new).collect(Collectors.toList()));
    styleTable.setBeans(election.ballot_styles.stream().map(BallotStyleBean::new).collect(Collectors.toList()));
    contestTable.setBeans(election.contests.stream().map(ContestBean::new).collect(Collectors.toList()));
    selectionTable.setBeans(new ArrayList<>());
  }

  void setContest(ContestBean contestBean) {
    selectionTable.setBeans(
            contestBean.contest.ballot_selections.stream().map(SelectionBean::new).collect(Collectors.toList()));
  }

  void save() {
    candidateTable.saveState(false);
    partyTable.saveState(false);
    gpunitTable.saveState(false);
    styleTable.saveState(false);
    contestTable.saveState(false);
    selectionTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    prefs.putInt("splitPos1", split1.getDividerLocation());
    prefs.putInt("splitPos2", split2.getDividerLocation());
    prefs.putInt("splitPos3", split3.getDividerLocation());
    prefs.putInt("splitPos4", split4.getDividerLocation());
    prefs.putInt("splitPos5", split5.getDividerLocation());
  }

  void validateElection() {
    if (this.election == null) {
      return;
    }
    ElectionInputValidation input = new ElectionInputValidation(this.election);
    Formatter problems = new Formatter();
    boolean ok = input.validateElection(problems);

    infoTA.setText(problems.toString());
    infoTA.appendLine(String.format("Manifest validates %s%n", ok));
    infoTA.gotoTop();
    infoWindow.show();
  }

  public static class PartyBean {
    Manifest.Party org;

    public PartyBean(){}

    PartyBean(Manifest.Party org) {
      this.org = org;
    }

    public String getId() {
      return org.object_id();
    }
    public String getAbbreviation() {
      return org.abbreviation.orElse("");
    }
    public String getLogoUri() {
      return org.logo_uri.orElse("");
    }
    public String getContactInformation() {
      return org.name.toString();
    }
    public String getColor() {
      return org.color.orElse("");
    }
  }

  public static class CandidateBean {
    Manifest.Candidate org;

    public CandidateBean(){}

    CandidateBean(Manifest.Candidate org) {
      this.org = org;
    }

    public String getId() {
      return org.object_id();
    }
    public String getPartyId() {
      return org.party_id.orElse("");
    }
    public String getImageUri() {
      return org.image_uri.orElse("");
    }
    public String getName() {
      return org.name.toString();
    }
    public boolean isWriteIn() {
      return org.is_write_in;
    }
  }

  public static class GpUnitBean {
    Manifest.GeopoliticalUnit gpunit;

    public GpUnitBean(){}

    GpUnitBean(Manifest.GeopoliticalUnit gpunit) {
      this.gpunit = gpunit;
    }

    public String getId() {
      return gpunit.object_id();
    }
    public String getType() {
      return gpunit.type.toString();
    }
    public String getName() {
      return gpunit.name;
    }
    public String getContactInformation() {
      return gpunit.contact_information.toString();
    }
  }

  public static class BallotStyleBean {
    Manifest.BallotStyle style;

    public BallotStyleBean(){}

    BallotStyleBean(Manifest.BallotStyle style) {
      this.style = style;
    }

    public String getBallotStyle() {
      return style.object_id();
    }
    public String getGeopolitical() {
      return style.geopolitical_unit_ids.toString();
    }
    public String getParties() {
      return style.party_ids.toString();
    }

    public String getImageUrl() {
      return style.image_uri.orElse("");
    }
  }

  public static class ContestBean {
    Manifest.ContestDescription contest;

    public ContestBean(){}

    ContestBean(Manifest.ContestDescription contest) {
      this.contest = contest;
    }
    public String getContestId() {
      return contest.object_id;
    }
    public String getName() {
      return contest.name;
    }
    public String getElectoralDistrictId() { return contest.electoral_district_id; }

    public String getBallotTitle() {
      if(contest.ballot_title.isPresent()) {
        return contest.ballot_title.get().text.toString();
      } else {
        return "";
      }
    }

    public String getBallotSubTitle() {
      if(contest.ballot_subtitle.isPresent()) {
        return contest.ballot_subtitle.get().text.toString();
      } else {
        return "";
      }
    }

    public String getCryptoHash() { return contest.crypto_hash().toString(); }
    public int getNumberElected() { return contest.number_elected; }
    public int getSeq() { return contest.sequence_order; }
    public String getVoteVariation() { return contest.vote_variation.toString(); }
    public int getVotesAllowed() { return contest.votes_allowed.orElse(-1); }
  }

  public static class SelectionBean {
    Manifest.SelectionDescription selection;

    public SelectionBean(){}

    SelectionBean(Manifest.SelectionDescription selection) {
      this.selection = selection;
    }

    public String getSelectionId() {
      return selection.object_id;
    }

    public String getCandidateId() {
      return selection.candidate_id;
    }

    public int getSeq() {
      return selection.sequence_order;
    }

    public String getCryptoHash() { return selection.crypto_hash().toString(); }
  }

}
