package org.alliance.ui.addfriendwizard;

import com.stendahls.XUI.XUIDialog;
import com.stendahls.ui.JHtmlLabel;
import com.stendahls.ui.JWizard;
import org.alliance.core.node.Invitation;
import org.alliance.core.LanguageResource;
import org.alliance.ui.T;
import org.alliance.ui.UISubsystem;
import org.alliance.ui.util.CutCopyPastePopup;
import org.alliance.ui.dialogs.OptionDialog;
import org.alliance.ui.themes.util.SubstanceThemeHelper;

import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by IntelliJ IDEA.
 * User: maciek
 * Date: 2006-apr-13
 * Time: 14:54:04
 */
public class AddFriendWizard extends JWizard {

    public static final int STEP_INTRO = 0;
    public static final int STEP_ENTER_INVITATION = 1;
    public static final int STEP_INCORRECT_INVITATION_CODE = 2;
    public static final int STEP_ATTEMPT_CONNECT = 3;
    public static final int STEP_CONNECTION_FAILED = 4;
    public static final int STEP_FORWARD_INVITATIONS = 5;
    public static final int STEP_FORWARD_INVITATIONS_COMPLETE = 6;
    public static final int STEP_CONNECTION_FAILED_FOR_FORWARD = 7;
    public static final int STEP_MANUAL_INVITE = 8;
    public static final int STEP_PORT_OPEN_TEST = 9;
    public static final int STEP_PORT_NOT_OPEN = 10;
    public static final int STEP_MANUAL_CONNECTION_OK = 11;
    private int radioButtonSelected;
    private UISubsystem ui;
    private XUIDialog outerDialog;
    private JTextField codeinput;
    private JTextArea invitationCode;
    private JScrollPane listScrollPane;
    private ArrayList<JRadioButton> radioButtons = new ArrayList<JRadioButton>();
    private Thread connectionThread;
    private ForwardInvitationNodesList forwardInvitationNodesList;
    private Integer invitationFromGuid;
    private static final String PORT_OPEN_TEST_URL = "http://maciek.tv/porttest?port=";

    public AddFriendWizard() throws Exception {
        setSuperTitle(LanguageResource.getLocalizedString(getClass(), "windowheader"));
    }

    @Override
    public void EVENT_cancel(ActionEvent e) throws Exception {
        Component c = getParent();
        while (!(c instanceof Window)) {
            c = c.getParent();
        }
        ((Window) c).setVisible(false);
        ((Window) c).dispose();
    }

    public void XUILayoutComplete(final UISubsystem ui, XUIDialog outerDialog) {
        this.ui = ui;
        this.outerDialog = outerDialog;
        innerXUI.setEventHandler(this);
        next.setEnabled(false);

        LanguageResource.translateXUIElements(getClass(), innerXUI.getXUIComponents());
        invitationCode = (JTextArea) innerXUI.getComponent("code");
        new CutCopyPastePopup(invitationCode);
        codeinput = (JTextField) innerXUI.getComponent("codeinput");
        new CutCopyPastePopup(codeinput);
        listScrollPane = (JScrollPane) innerXUI.getComponent("scrollpanel");

        radioButtons.add((JRadioButton) innerXUI.getComponent("radio1_1"));
        radioButtons.add((JRadioButton) innerXUI.getComponent("radio1_2"));
        radioButtons.add((JRadioButton) innerXUI.getComponent("radio1_3"));

        JHtmlLabel portclosed = (JHtmlLabel) innerXUI.getComponent("portclosed");
        portclosed.setText(LanguageResource.getLocalizedString(getClass(), "xui.portclosed",
                Integer.toString(ui.getCore().getSettings().getServer().getPort()),
                "[a href=http://www.portforward.com]http://www.portforward.com[/a]"));
        portclosed.addHyperlinkListener(new HyperlinkListener() {

            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    ui.openURL(e.getDescription());
                }
            }
        });

        //disable looking for friends in secondary connections if we have no friends
        //or if we have no friends to forward invitations to  
        if (new ForwardInvitationNodesList.ForwardInvitationListModel(ui.getCore()).getSize() == 0
                || ui.getCore().getFriendManager().friends().size() == 0) {
            innerXUI.getComponent("radio1_3").setEnabled(false);
        }

        final JHtmlLabel newcode = (JHtmlLabel) innerXUI.getComponent("newcode");
        newcode.setText(LanguageResource.getLocalizedString(getClass(), "xui.newcode",
                "[a href=.]" + LanguageResource.getLocalizedString(getClass(), "xui.newcodegen") + "[/a]"));
        newcode.addHyperlinkListener(new HyperlinkListener() {

            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    EVENT_createnew(null);
                }
            }
        });
    }

    public static AddFriendWizard open(UISubsystem ui, int startAtStep) throws Exception {
        XUIDialog f = new XUIDialog(ui.getRl(), ui.getRl().getResourceStream("xui/addfriendwizard.xui.xml"), ui.getMainWindow());
        final AddFriendWizard wizard = (AddFriendWizard) f.getXUI().getComponent("wizard");
        SubstanceThemeHelper.setButtonsToGeneralArea(wizard.getXUIComponents());
        wizard.XUILayoutComplete(ui, f);
        if (startAtStep == STEP_FORWARD_INVITATIONS) {
            wizard.goToForwardInvitations();
        } else if (startAtStep == STEP_ATTEMPT_CONNECT) {
            wizard.goToAttemptConnect();
        } else if (startAtStep == STEP_PORT_OPEN_TEST) {
            wizard.goToPortTest();
        } else if (startAtStep != STEP_INTRO) {
            throw new Exception("No support for starting at step " + startAtStep);
        }
        return wizard;
    }

    private void goToEnterInvitation() {
        setStep(STEP_ENTER_INVITATION);
        codeinput.requestFocus();
    }

    private void goToManualInvite() {
        setStep(STEP_MANUAL_INVITE);
        prev.setEnabled(true);
        next.setEnabled(false);
        cancel.setEnabled(true);
        cancel.setText(LanguageResource.getLocalizedString(getClass(), "finish"));
    }

    public void goToPortTest() {
        if (ui.getCore().getSettings().getServer().getLansupport() != null && ui.getCore().getSettings().getServer().getLansupport() == 1) {
            //alliance is to be run on an internal LAN - don't do port test
            goToCreateInvitation();
            return;
        }
        setStep(STEP_PORT_OPEN_TEST);
        prev.setEnabled(false);
        next.setEnabled(false);
        cancel.setEnabled(false);

        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    String result = getResponseFromURL(PORT_OPEN_TEST_URL + ui.getCore().getSettings().getServer().getPort());
                    if (T.t) {
                        T.info("Result from port test: " + result);
                    }
                    if ("OPEN".equals(result)) {
                        SwingUtilities.invokeLater(new Runnable() {

                            @Override
                            public void run() {
                                goToCreateInvitation();
                            }
                        });
                    } else if ("CLOSED".equals(result)) {
                        SwingUtilities.invokeLater(new Runnable() {

                            @Override
                            public void run() {
                                prev.setEnabled(true);
                                next.setEnabled(false);
                                cancel.setEnabled(true);
                                cancel.setText(LanguageResource.getLocalizedString(getClass(), "finish"));
                                setStep(STEP_PORT_NOT_OPEN);
                            }
                        });
                    } else {
                        if (T.t) {
                            T.error("Could not test if port is open: " + result);
                        }
                        SwingUtilities.invokeLater(new Runnable() {

                            @Override
                            public void run() {
                                goToCreateInvitation();
                            }
                        });
                    }
                } catch (Exception e) {
                    ui.handleErrorInEventLoop(e);
                }
            }
        });
        t.start();
    }

    private String getResponseFromURL(String url) throws IOException {
        URLConnection c = new URL(url).openConnection();
        InputStream in = c.getInputStream();
        StringBuffer result = new StringBuffer();
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = r.readLine()) != null) {
            result.append(line);
        }

        line = result.toString();
        return line;
    }

    private void goToCreateInvitation() {
        EVENT_createnew(null);
        goToManualInvite();
    }

    private void goToConnectionFailed() {
        next.setEnabled(false);
        if (invitationFromGuid != null) {
            setStep(STEP_CONNECTION_FAILED_FOR_FORWARD);
            next.setEnabled(false);
            prev.setEnabled(false);
            cancel.setText(LanguageResource.getLocalizedString(getClass(), "finish"));
        } else {
            setStep(STEP_CONNECTION_FAILED);
        }
    }

    private void goToConnectionOk() {
        setStep(STEP_MANUAL_CONNECTION_OK);
        next.setEnabled(false);
        prev.setEnabled(false);
    }

    @Override
    public void setStep(int i) {
        super.setStep(i);
        if (getSteptitle() != null && !getSteptitle().isEmpty()) {
            super.changeTitle(LanguageResource.getLocalizedString(getClass(), "xui." + getSteptitle().replace("%", "")));
        }
        resetAllRadioButtons();
    }

    private void resetAllRadioButtons() {
        for (JRadioButton b : radioButtons) {
            if (b != null) {
                b.setSelected(false);
            }
        }
    }

    @Override
    public void nextStep() {
        if (getStep() == STEP_INTRO) {
            if (radioButtonSelected == 0) {
                goToEnterInvitation();
            } else if (radioButtonSelected == 1) {
                goToPortTest();
            } else {
                goToForwardInvitations();
            }
        } else if (getStep() == STEP_ENTER_INVITATION) {
            handleInvitationCode();
        } else if (getStep() == STEP_CONNECTION_FAILED) {
            goToPortTest();
        } else if (getStep() == STEP_FORWARD_INVITATIONS) {
            if (forwardInvitationNodesList != null) {
                forwardInvitationNodesList.forwardSelectedInvitations();
            }
            setStep(STEP_FORWARD_INVITATIONS_COMPLETE);
            next.setEnabled(false);
            cancel.setText(LanguageResource.getLocalizedString(getClass(), "finish"));
        } else {
            if (T.t) {
                T.ass(false, "Reached step in wizard that was unexcpected (" + getStep() + ")");
            }
        }
    }

    public void connectionWasSuccessful() {
        if (connectionThread != null) {
            connectionThread.interrupt();
        }
    }

    public void goToForwardInvitations() {
        connectionWasSuccessful();
        listScrollPane.setViewportView(forwardInvitationNodesList = new ForwardInvitationNodesList(ui, this));
        setStep(STEP_FORWARD_INVITATIONS);
        next.setEnabled(false);
        if (forwardInvitationNodesList.getModel().getSize() == 0) {
            getOuterDialog().dispose(); //we're done. Nothing to forward. Just close the wizard.
        }
    }

    private void handleInvitationCode() {
        String invitation = codeinput.getText().trim();
        if (invitation.length() == 0) {
            OptionDialog.showErrorDialog(ui.getMainWindow(), LanguageResource.getLocalizedString(getClass(), "nocode"));
        } else {
            try {
                ui.getCore().getInvitaitonManager().attemptToBecomeFriendWith(invitation.trim(), null);
                goToAttemptConnect();
            } catch (EOFException ex) {
                OptionDialog.showErrorDialog(ui.getMainWindow(), LanguageResource.getLocalizedString(getClass(), "shortcode"));
                goToEnterInvitation();
            } catch (Exception e) {
                ui.handleErrorInEventLoop(e);
                goToConnectionFailed();
            }
        }
    }

    public void goToAttemptConnect() {
        connectionThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(1000 * 20);
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            goToConnectionFailed();
                        }
                    });
                } catch (InterruptedException e) {
                    if (T.t) {
                        T.info("Looks like we connected succesfully.");
                    }
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            goToConnectionOk();
                        }
                    });
                }
            }
        });
        connectionThread.start();
        setStep(STEP_ATTEMPT_CONNECT);
        next.setEnabled(false);
        prev.setEnabled(false);
    }

    @Override
    public void prevStep() {
        if (getStep() == STEP_FORWARD_INVITATIONS) {
            setStep(STEP_INTRO);
        } else if (getStep() == STEP_ENTER_INVITATION) {
            setStep(STEP_INTRO);
        } else if (getStep() == STEP_CONNECTION_FAILED) {
            setStep(STEP_ENTER_INVITATION);
        } else if (getStep() == STEP_MANUAL_INVITE) {
            setStep(STEP_INTRO);
        } else if (getStep() == STEP_PORT_NOT_OPEN) {
            setStep(STEP_INTRO);
        } else if (getStep() == STEP_PORT_OPEN_TEST) {
            setStep(STEP_INTRO);
        } else {
            super.prevStep();
        }
    }

    public void EVENT_radio1_1(ActionEvent e) {
        radioButtonSelected = 0;
        next.setEnabled(true);
    }

    public void EVENT_radio1_2(ActionEvent e) {
        radioButtonSelected = 1;
        next.setEnabled(true);
    }

    public void EVENT_radio1_3(ActionEvent e) {
        radioButtonSelected = 2;
        next.setEnabled(true);
    }

    public void EVENT_radio2_1(ActionEvent e) {
        radioButtonSelected = 0;
        next.setEnabled(true);
    }

    public void EVENT_radio2_2(ActionEvent e) {
        radioButtonSelected = 1;
        next.setEnabled(true);
    }

    public void EVENT_createnew(ActionEvent e) {
        invitationCode.setText(LanguageResource.getLocalizedString(getClass(), "generatecode"));
        invitationCode.revalidate();
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    final Invitation i = ui.getCore().getInvitaitonManager().createInvitation();
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            invitationCode.setText("");
                            invitationCode.append(LanguageResource.getLocalizedString(getClass(), "invline1"));
                            invitationCode.append("\n\n");
                            invitationCode.append(LanguageResource.getLocalizedString(getClass(), "invline2"));
                            invitationCode.append("\n");
                            invitationCode.append("http://www.alliancep2p.com/download\n\n");
                            invitationCode.append(LanguageResource.getLocalizedString(getClass(), "invline3"));
                            invitationCode.append("\n");
                            invitationCode.append(i.getCompleteInvitaitonString());
                            invitationCode.requestFocus();
                            SwingUtilities.invokeLater(new Runnable() {

                                @Override
                                public void run() {
                                    invitationCode.selectAll();
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    ui.handleErrorInEventLoop(e);
                }
            }
        });
        t.start();
    }

    public void EVENT_selectall(ActionEvent e) {
        forwardInvitationNodesList.selectAll();
    }

    public void EVENT_selecttrusted(ActionEvent e) {
        forwardInvitationNodesList.selectTrusted();
    }

    public void EVENT_selectnone(ActionEvent e) {
        forwardInvitationNodesList.selectNone();
    }

    public XUIDialog getOuterDialog() {
        return outerDialog;
    }

    public void enableNext() {
        next.setEnabled(true);
    }

    public void setInvitationFromGuid(Integer invitationFromGuid) {
        this.invitationFromGuid = invitationFromGuid;
    }
}