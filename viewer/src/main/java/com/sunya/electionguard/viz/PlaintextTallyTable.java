/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.Optional;

public class PlaintextTallyTable extends JPanel {
  private final PreferencesExt prefs;

  private final BeanTable<PlaintextTallyBean> tallyTable;
  private final BeanTable<ContestBean> contestTable;
  private final BeanTable<SelectionBean> selectionTable;

  private final JSplitPane split1;
  private final JSplitPane split2;

  private final IndependentWindow infoWindow;

  private InternalManifest manifest;

  public PlaintextTallyTable(PreferencesExt prefs) {
    this.prefs = prefs;
    TextHistoryPane infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("electionguard-logo.png"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 100)));

    tallyTable = new BeanTable<>(PlaintextTallyBean.class, (PreferencesExt) prefs.node("TallyTable"), false,
            "PlaintextTally", "PlaintextTally", null);
    tallyTable.addListSelectionListener(e -> {
      PlaintextTallyBean tallyBean = tallyTable.getSelectedBean();
      if (tallyBean != null) {
        setTally(tallyBean);
      }
    });
    tallyTable.addPopupOption("Show Tally", tallyTable.makeShowAction(infoTA, infoWindow,
            bean -> ((PlaintextTallyBean)bean).tally.toString()));

    contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("ContestTable"), false,
            "Contest", "PlaintextTally.Contest", null);
    contestTable.addListSelectionListener(e -> {
      ContestBean contest = contestTable.getSelectedBean();
      if (contest != null) {
        setContest(contest);
      }
    });
    contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
            bean -> bean.toString()));

    selectionTable = new BeanTable<>(SelectionBean.class, (PreferencesExt) prefs.node("SelectionTable"), false,
            "Selection", "PlaintextTally.Selection", null);
    selectionTable.addPopupOption("Show Selection", selectionTable.makeShowAction(infoTA, infoWindow,
            bean -> bean.toString()));

    // layout
    split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, tallyTable, contestTable);
    split1.setDividerLocation(prefs.getInt("splitPos1", 200));

    split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, selectionTable);
    split2.setDividerLocation(prefs.getInt("splitPos2", 200));

    setLayout(new BorderLayout());
    add(split2, BorderLayout.CENTER);
  }

  void setPlaintextTallies(Manifest manifest, CloseableIterable<PlaintextTally> tallies) {
    this.manifest = new InternalManifest(manifest);
    try (CloseableIterator<PlaintextTally> iter = tallies.iterator())  {
      java.util.List<PlaintextTallyBean> beanList = new ArrayList<>();
      while (iter.hasNext()) {
        PlaintextTally tally = iter.next();
        beanList.add(new PlaintextTallyBean(tally));
      }
      tallyTable.setBeans(beanList);
      if (beanList.size() > 0) {
        setTally(beanList.get(0));
      } else {
        contestTable.setBeans(new ArrayList<>());
        selectionTable.setBeans(new ArrayList<>());
      }
    }
  }

  void setTally(PlaintextTallyBean plaintextTallyBean) {
    java.util.List<ContestBean> beanList = new ArrayList<>();
    for (PlaintextTally.Contest c : plaintextTallyBean.tally.contests.values()) {
      beanList.add(new ContestBean(c));
    }
    contestTable.setBeans(beanList);
    if (beanList.size() > 0) {
      setContest(beanList.get(0));
    } else {
      selectionTable.setBeans(new ArrayList<>());
    }
  }

  void setContest(ContestBean contestBean) {
    java.util.List<SelectionBean> beanList = new ArrayList<>();
    for (PlaintextTally.Selection s : contestBean.contest.selections().values()) {
      beanList.add(new SelectionBean(s, contestBean));
    }
    selectionTable.setBeans(beanList);
  }

  void save() {
    tallyTable.saveState(false);
    contestTable.saveState(false);
    selectionTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    prefs.putInt("splitPos1", split1.getDividerLocation());
    prefs.putInt("splitPos2", split2.getDividerLocation());
  }

  void showInfo(Formatter f) {
    f.format(" Current time =   %s%n%n", new Date().toString());
  }

  public class PlaintextTallyBean {
    PlaintextTally tally;

    public PlaintextTallyBean(){}

    PlaintextTallyBean(PlaintextTally tally) {
      this.tally = tally;
    }

    public String getId() {
      return tally.tallyId;
    }
  }

  public class ContestBean {
    PlaintextTally.Contest contest;
    Optional<InternalManifest.ContestWithPlaceholders> descOpt;

    public ContestBean(){}

    ContestBean(PlaintextTally.Contest contest) {
      this.contest = contest;
      this.descOpt= manifest.getContestById(contest.contestId());
    }
    public String getContestId() {
      return contest.contestId();
    }

    public String getName() {
      return descOpt.map( desc -> desc.contest.name()).orElse( "N/A");
    }

    public String getVoteVariation() {
      return descOpt.map( desc -> desc.contest.voteVariation().toString()).orElse( "N/A");
    }

    public String getVotes() {
      Formatter f = new Formatter();
      java.util.List<PlaintextTally.Selection> sorted = contest.selections().values().stream()
              .sorted((s1, s2) -> s2.tally().compareTo(s1.tally())).toList();
      for (PlaintextTally.Selection s : sorted) {
        Manifest.SelectionDescription sd = descOpt.get().getSelectionById(s.selectionId()).get();
        f.format("%s:%d, ", sd.candidateId(), s.tally());
      }
      return f.toString();
    }

    public String getWinner() {
      java.util.List<PlaintextTally.Selection> sorted = contest.selections().values().stream()
              .sorted((s1, s2) -> s2.tally().compareTo(s1.tally())).toList();
      PlaintextTally.Selection s = sorted.get(0);
      Manifest.SelectionDescription sd = descOpt.get().getSelectionById(s.selectionId()).get();
      return sd.candidateId();
    }

    @Override
    public String toString() {
      return String.format("PlaintextTally.Contest%n %s%n%nManifest.ContestDescription%n%s%n",
              contest , descOpt.map( d -> d.contest.toString()).orElse("N/A"));
    }
  }

  public class SelectionBean {
    PlaintextTally.Selection selection;
    Optional<Manifest.SelectionDescription> descOpt;

    public SelectionBean(){}

    SelectionBean(PlaintextTally.Selection selection, ContestBean contestBean) {
      this.selection = selection;
      if (contestBean.descOpt.isPresent()) {
        InternalManifest.ContestWithPlaceholders contestDesc = contestBean.descOpt.get();
        this.descOpt = contestDesc.getSelectionById(selection.selectionId());
      } else {
        this.descOpt = Optional.empty();
      }
    }

    public String getSelectionId() {
      return selection.selectionId();
    }

    public String getCandidateId() {
      return descOpt.map( desc -> desc.candidateId()).orElse( "N/A");
    }

    public int getTally() {
      return selection.tally();
    }

    public int getNShares() {
      return selection.shares().size();
    }

    @Override
    public String toString() {
      return String.format("PlaintextTally.Selection%n %s%n%nManifest.SelectionDescription%n%s%n",
              selection , descOpt.map(d -> d.toString()).orElse("N/A"));
    }

  }

}