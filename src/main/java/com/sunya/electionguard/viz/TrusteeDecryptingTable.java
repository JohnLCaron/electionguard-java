/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package com.sunya.electionguard.viz;

import com.sunya.electionguard.guardian.DecryptingTrustee;
import com.sunya.electionguard.guardian.KeyCeremony2;
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
import java.util.Map;

public class TrusteeDecryptingTable extends JPanel {
  private final PreferencesExt prefs;

  private final BeanTable<TrusteeBean> trusteeTable;
  private final BeanTable<PartialKeyBackupBean> backupTable;

  private final JSplitPane split1;

  private final TextHistoryPane infoTA;
  private final IndependentWindow infoWindow;

  public TrusteeDecryptingTable(PreferencesExt prefs) {
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

    // layout
    split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, trusteeTable, backupTable);
    split1.setDividerLocation(prefs.getInt("splitPos1", 200));

    setLayout(new BorderLayout());
    add(split1, BorderLayout.CENTER);
  }

  void setTrustees(Iterable<DecryptingTrustee> trustees) {
    java.util.List<TrusteeBean> beanList = new ArrayList<>();
    for (DecryptingTrustee trustee : trustees)  {
      beanList.add(new TrusteeBean(trustee));
    }
    trusteeTable.setBeans(beanList);
  }

  void setTrustee(TrusteeBean trusteeBean) {
    java.util.List<PartialKeyBackupBean> beanList = new ArrayList<>();
    for (Map.Entry<String, KeyCeremony2.PartialKeyBackup> e : trusteeBean.object.otherGuardianPartialKeyBackups.entrySet()) {
      beanList.add(new PartialKeyBackupBean(e.getKey(), e.getValue()));
    }
    backupTable.setBeans(beanList);
  }

  void save() {
    backupTable.saveState(false);
    trusteeTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    prefs.putInt("splitPos1", split1.getDividerLocation());
  }

  void showInfo(Formatter f) {
    f.format(" Current time =   %s%n%n", new Date().toString());
  }

  public class TrusteeBean {
    DecryptingTrustee object;

    public TrusteeBean(){}

    TrusteeBean(DecryptingTrustee object) {
      this.object = object;
    }

    public String getId() {
      return object.id;
    }
    public int getSequence() {
      return object.sequence_order;
    }
    public String getElectionPrivateKey() {
      return object.election_keypair.secret_key.toString();
    }
    public String getElectionPublicKey() {
      return object.election_keypair.public_key.toShortString();
    }
    public String getRsaPrivateKey() {
      return object.rsa_private_key.toString();
    }
    public int getCommitmentSize() {
      return object.guardianCommittments.size();
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
    public String getOwnerId() {
      return object.generatingGuardianId();
    }
    public String getDesignatedId() {
      return object.designatedGuardianId();
    }
    public int getDesignatedSequence() {
      return object.designatedGuardianXCoordinate();
    }
  }

}
