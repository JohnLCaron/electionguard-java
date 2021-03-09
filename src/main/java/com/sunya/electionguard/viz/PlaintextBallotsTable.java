/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.sunya.electionguard.PlaintextBallot;
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

public class PlaintextBallotsTable extends JPanel {
  private final PreferencesExt prefs;

  private final BeanTable<BallotBean> ballotTable;
  private final BeanTable<ContestBean> contestTable;
  private final BeanTable<SelectionBean> selectionTable;

  private final JSplitPane split1, split2;
  private final IndependentWindow infoWindow;

  public PlaintextBallotsTable(PreferencesExt prefs) {
    this.prefs = prefs;
    TextHistoryPane infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("electionguard-logo.png"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 100)));

    ballotTable = new BeanTable<>(BallotBean.class, (PreferencesExt) prefs.node("BallotTable"), false);
    ballotTable.addListSelectionListener(e -> {
      BallotBean ballot = ballotTable.getSelectedBean();
      if (ballot != null) {
        setBallot(ballot);
      }
    });
    ballotTable.addPopupOption("Show Ballot", ballotTable.makeShowAction(infoTA, infoWindow,
            bean -> ((BallotBean)bean).ballot.toString()));

    contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("ContestTable"), false);
    contestTable.addListSelectionListener(e -> {
      ContestBean contest = contestTable.getSelectedBean();
      if (contest != null) {
        setContest(contest);
      }
    });
    contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
            bean -> ((ContestBean)bean).contest.toString()));

    selectionTable = new BeanTable<>(SelectionBean.class, (PreferencesExt) prefs.node("SelectionTable"), false);
    selectionTable.addPopupOption("Show Selection", selectionTable.makeShowAction(infoTA, infoWindow,
            bean -> ((SelectionBean)bean).selection.toString()));

    // layout
    split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, ballotTable, contestTable);
    split1.setDividerLocation(prefs.getInt("splitPos1", 200));

    split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, selectionTable);
    split2.setDividerLocation(prefs.getInt("splitPos2", 200));

    setLayout(new BorderLayout());
    add(split2, BorderLayout.CENTER);
  }

  void setBallots(CloseableIterable<PlaintextBallot> ballots) {

    try (CloseableIterator<PlaintextBallot> iter = ballots.iterator())  {
      java.util.List<BallotBean> beanList = new ArrayList<>();
      while (iter.hasNext()) {
        PlaintextBallot ballot = iter.next();
        beanList.add(new BallotBean(ballot));
      }
      ballotTable.setBeans(beanList);
      if (beanList.size() > 0) {
        setBallot(beanList.get(0));
      } else {
        contestTable.setBeans(new ArrayList<>());
        selectionTable.setBeans(new ArrayList<>());
      }
    }
  }

  void setBallot(BallotBean ballotBean) {
    java.util.List<ContestBean> beanList = new ArrayList<>();
    for (PlaintextBallot.Contest c : ballotBean.ballot.contests) {
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
    for (PlaintextBallot.Selection s : contestBean.contest.ballot_selections) {
      beanList.add(new SelectionBean(s));
    }
    selectionTable.setBeans(beanList);
  }

  void save() {
    ballotTable.saveState(false);
    contestTable.saveState(false);
    selectionTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    prefs.putInt("splitPos1", split1.getDividerLocation());
    prefs.putInt("splitPos2", split2.getDividerLocation());
  }

  public static class BallotBean {
    PlaintextBallot ballot;

    public BallotBean(){}

    BallotBean(PlaintextBallot ballot) {
      this.ballot = ballot;
    }

    public String getId() {
      return ballot.object_id;
    }

    public String getStyle() {
      return ballot.style_id;
    }
  }

  public static class ContestBean {
    PlaintextBallot.Contest contest;

    public ContestBean(){}

    ContestBean(PlaintextBallot.Contest contest) {
      this.contest = contest;
    }

    public String getContestId() {
      return contest.contest_id;
    }
  }

  public static class SelectionBean {
    PlaintextBallot.Selection selection;

    public SelectionBean(){}

    SelectionBean(PlaintextBallot.Selection selection) {
      this.selection = selection;
    }

    public String getSelectionId() {
      return selection.selection_id;
    }

    public boolean isPlaceHolder() {
      return selection.is_placeholder_selection;
    }

    public int getVote() {
      return selection.vote;
    }

    public String getExtendedData() {
      if (selection.extended_data.isPresent()) {
        return selection.extended_data.get().value;
      } else {
        return "";
      }
    }

  }

}
