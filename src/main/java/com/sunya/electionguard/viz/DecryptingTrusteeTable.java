/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.decrypting.DecryptingTrustee;
import com.sunya.electionguard.keyceremony.KeyCeremony2;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Map;

public class DecryptingTrusteeTable extends JPanel {
  private final PreferencesExt prefs;

  private final BeanTable<TrusteeBean> trusteeTable;
  private final BeanTable<PartialKeyBackupBean> backupTable;
  private final BeanTable<GuardianCommittmentsBean> commitmentTable;

  private final JSplitPane split1;
  private final JSplitPane split2;

  private final TextHistoryPane infoTA;
  private final IndependentWindow infoWindow;

  private DecryptingTrustee current;

  public DecryptingTrusteeTable(PreferencesExt prefs) {
    this.prefs = prefs;
    infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("electionguard-logo.png"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 100)));

    trusteeTable = new BeanTable<>(TrusteeBean.class, (PreferencesExt) prefs.node("ContestTable"), false);
    trusteeTable.addListSelectionListener(e -> {
      TrusteeBean bean = trusteeTable.getSelectedBean();
      if (bean != null) {
        setTrustee(bean);
      }
    });

    backupTable = new BeanTable<>(PartialKeyBackupBean.class, (PreferencesExt) prefs.node("backupTable"), false);
    commitmentTable = new BeanTable<>(GuardianCommittmentsBean.class, (PreferencesExt) prefs.node("commTable"), false);

    // layout
    split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, backupTable, commitmentTable);
    split2.setDividerLocation(prefs.getInt("splitPos2", 200));

    split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, trusteeTable, split2);
    split1.setDividerLocation(prefs.getInt("splitPos1", 200));

    setLayout(new BorderLayout());
    add(split1, BorderLayout.CENTER);
  }

  void setTrustees(Iterable<DecryptingTrustee> trustees) {
    this.current = null;
    java.util.List<TrusteeBean> beanList = new ArrayList<>();
    for (DecryptingTrustee trustee : trustees)  {
      if (this.current == null) {
        this.current = trustee;
      }
      beanList.add(new TrusteeBean(trustee));
    }
    trusteeTable.setBeans(beanList);
  }

  void setTrustee(TrusteeBean trusteeBean) {
    this.current = trusteeBean.object;

    java.util.List<PartialKeyBackupBean> beanList = new ArrayList<>();
    for (Map.Entry<String, KeyCeremony2.PartialKeyBackup> e : trusteeBean.object.otherGuardianPartialKeyBackups.entrySet()) {
      beanList.add(new PartialKeyBackupBean(e.getKey(), e.getValue()));
    }
    backupTable.setBeans(beanList);

    java.util.List<GuardianCommittmentsBean> bean2List = new ArrayList<>();
    for (Map.Entry<String, ImmutableList<Group.ElementModP>> e : trusteeBean.object.guardianCommittments.entrySet()) {
      bean2List.add(new GuardianCommittmentsBean(e.getKey(), e.getValue()));
    }
    commitmentTable.setBeans(bean2List);
  }

  void save() {
    backupTable.saveState(false);
    trusteeTable.saveState(false);
    commitmentTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    prefs.putInt("splitPos1", split1.getDividerLocation());
    prefs.putInt("splitPos2", split2.getDividerLocation());
  }

  void showInfo(Formatter f) {
    if (this.current != null) {
      f.format("%s%n", this.current);
    }
  }

  public class TrusteeBean {
    DecryptingTrustee object;
    public TrusteeBean(){}
    TrusteeBean(DecryptingTrustee object) {
      this.object = object;
    }

    public String getId() {
      return object.id();
    }
    public int getXCoordinate() {
      return object.xCoordinate();
    }
    public String getElectionPrivateKey() {
      return object.election_keypair.secret_key.toString();
    }
    public String getElectionPublicKey() {
      return object.election_keypair.public_key.toShortString();
    }
  }

  public class PartialKeyBackupBean {
    String key;
    KeyCeremony2.PartialKeyBackup object;

    public PartialKeyBackupBean(){}

    PartialKeyBackupBean(String key, KeyCeremony2.PartialKeyBackup object) {
      this.key = key;
      this.object = object;
    }

    public String getKey() {
      return key;
    }
    public String getGeneratingGuardianId() {
      return object.generatingGuardianId();
    }
    public String getDesignatedGuardianId() {
      return object.designatedGuardianId();
    }
    public int getDesignatedXCoordinate() {
      return object.designatedGuardianXCoordinate();
    }
    public String getCoordinate() {
      return object.coordinate().toString();
    }
  }

  public class GuardianCommittmentsBean {
    String key;
    ImmutableList<Group.ElementModP> object;

    public GuardianCommittmentsBean(){}

    GuardianCommittmentsBean(String key, ImmutableList<Group.ElementModP> object) {
      this.key = key;
      this.object = object;
    }

    public String getKey() {
      return key;
    }

    public String getCommitments() {
      Formatter f = new Formatter();
      for (Group.ElementModP modp :  object) {
        f.format("%s, ", modp.toShortString());
      }
      return f.toString();
    }
  }

}
