/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.sunya.electionguard.Election;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterator;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.stream.Collectors;

public class ElectionDescriptionTable extends JPanel {
  private PreferencesExt prefs;

  private BeanTable<BallotStyleBean> styleTable;
  private BeanTable<ContestBean> contestTable;
  private BeanTable<SelectionBean> selectionTable;
  private BeanTable<PartyBean> partyTable;
  private BeanTable<CandidateBean> candidateTable;
  private BeanTable<GpUnitBean> gpunitTable;

  private JSplitPane split1, split2, split3, split4, split5;

  private TextHistoryPane infoTA;
  private IndependentWindow infoWindow;

  public ElectionDescriptionTable(PreferencesExt prefs) {
    this.prefs = prefs;

    contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("ContestTable"), false,
            "Contest", "Election.ContestDescription", null);
    contestTable.addListSelectionListener(e -> {
      ContestBean contest = contestTable.getSelectedBean();
      if (contest != null) {
        setContest(contest);
      }
    });

    styleTable = new BeanTable<>(BallotStyleBean.class, (PreferencesExt) prefs.node("StyleTable"), false,
            "BallotStyle", "Election.BallotStyle", null);
    selectionTable = new BeanTable<>(SelectionBean.class, (PreferencesExt) prefs.node("SelectionTable"), false,
            "Selection", "Election.Selection", null);
    partyTable = new BeanTable<>(PartyBean.class, (PreferencesExt) prefs.node("PartyTable"), false,
            "Party", "Election.Party", null);
    candidateTable = new BeanTable<>(CandidateBean.class, (PreferencesExt) prefs.node("CandidateTable"), false,
            "Candidate", "Election.Candidate", null);
    gpunitTable = new BeanTable<>(GpUnitBean.class, (PreferencesExt) prefs.node("GpUnitTable"), false,
            "GeopoliticalUnit", "Election.GeopoliticalUnit", null);

    // the info window
    infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("electionguard-logo.png"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 100)));

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

  void setElectionDescription(Election election) {
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

  public class PartyBean {
    Election.Party org;

    public PartyBean(){}

    PartyBean(Election.Party org) {
      this.org = org;
    }

    public String getId() {
      return org.object_id;
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

  public class CandidateBean {
    Election.Candidate org;

    public CandidateBean(){}

    CandidateBean(Election.Candidate org) {
      this.org = org;
    }

    public String getId() {
      return org.object_id;
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

  public class GpUnitBean {
    Election.GeopoliticalUnit gpunit;

    public GpUnitBean(){}

    GpUnitBean(Election.GeopoliticalUnit gpunit) {
      this.gpunit = gpunit;
    }

    public String getId() {
      return gpunit.object_id;
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

  public class BallotStyleBean {
    Election.BallotStyle style;

    public BallotStyleBean(){}

    BallotStyleBean(Election.BallotStyle style) {
      this.style = style;
    }

    public String getBallotStyle() {
      return style.object_id;
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

  public class ContestBean {
    Election.ContestDescription contest;

    public ContestBean(){}

    ContestBean(Election.ContestDescription contest) {
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

  public class SelectionBean {
    Election.SelectionDescription selection;

    public SelectionBean(){}

    SelectionBean(Election.SelectionDescription selection) {
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
