package client;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class ChatScreen1 implements ActionListener, WindowListener
{

	private String				user;
	final JFrame				chatScreen;
	JEditorPane					messageBox;
	JTextPane					conversationBox;
	private MessageStructure	chatStructure;
	ChatClientFactory			factory	= new ChatClientFactory();
	StyledDocument				doc;
	javax.swing.text.Style		bold, plain;
	JButton						sendButton;
	JScrollPane					conversationScroller;

	public static void main( String[] args )
	{
		ChatScreen1 cht1 = new ChatScreen1( "Trial" );
		cht1.createScreen();
		cht1.showUI();
		cht1.updateConversation(
				"user",
				"To start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat clientTo start the chat client" );

	}

	public ChatScreen1( String usr )
	{
		this.user = usr;
		chatScreen = new JFrame( "Chat with " + user );
		chatStructure = new MessageStructure( ChatClient.MSG_CODE_4, ChatClient.hostUser, user, "" );

	}

	public void createScreen()
	{

		GridBagLayout chatLayout = new GridBagLayout();

		chatScreen.setLayout( chatLayout );
		chatScreen.setSize( 600, 600 );
		chatScreen.setResizable( false );
		chatScreen.setLocationRelativeTo( null );
		chatScreen.getContentPane().setBackground( ChatClient.BACKGROUND_COLOR );

		chatScreen.addWindowListener( this );

		// Message History label

		JLabel chatHistory = new JLabel( "Chat History" );
		chatHistory.setFont( ChatClient.FONT_L2 );

		GridBagConstraints historyContraints = new GridBagConstraints();
		historyContraints.anchor = GridBagConstraints.NORTHWEST;
		chatScreen.add( chatHistory, historyContraints );

		// Conversation 

		JLabel conversation = new JLabel( "Type your message below:" );
		conversation.setFont( ChatClient.FONT_L2 );

		GridBagConstraints conversationConstraint = new GridBagConstraints();
		conversationConstraint.gridy = 2;
		conversationConstraint.anchor = GridBagConstraints.NORTHWEST;
		conversationConstraint.insets = new Insets( 20, 0, 0, 0 );
		chatScreen.add( conversation, conversationConstraint );

		// Conversation Box
		conversationBox = new JTextPane();
		conversationBox.setEditable( false );
		conversationBox.setFont( ChatClient.FONT_L1 );
		doc = conversationBox.getStyledDocument();

		bold = doc.addStyle( "bold", null );
		StyleConstants.setBold( bold, true );

		plain = doc.addStyle( "plain", null );
		StyleConstants.setBold( plain, false );

		conversationScroller = new JScrollPane( conversationBox );
		conversationScroller.setPreferredSize( new Dimension( 500, 300 ) );

		GridBagConstraints conversationConstraints = new GridBagConstraints();
		conversationConstraints.gridy = 1;
		conversationConstraints.insets = new Insets( 10, 0, 0, 0 );

		chatScreen.add( conversationScroller, conversationConstraints );

		// Message Box
		messageBox = new JEditorPane();
		messageBox.setFont( ChatClient.FONT_L1 );
		//messageBox.addKeyListener( this);
		messageBox.getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "none" );

		Action enterPressed = new AbstractAction()
		{
			public void actionPerformed( ActionEvent e )
			{
				messageBox.setText( messageBox.getText() + "\n" );
			}

		};

		messageBox.getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK ), "enter" );
		messageBox.getActionMap().put( "enter", enterPressed );

		JScrollPane messageScroller = new JScrollPane( messageBox );
		messageScroller.setPreferredSize( new Dimension( 500, 100 ) );

		GridBagConstraints messageConstraints = new GridBagConstraints();
		messageConstraints.gridy = 3;
		messageConstraints.insets = new Insets( 10, 0, 0, 0 );

		chatScreen.add( messageScroller, messageConstraints );

		// Send Button

		sendButton = new JButton( "Send" );
		sendButton.setFont( ChatClient.FONT_L2 );
		sendButton.addActionListener( this );

		GridBagConstraints sendConstraints = new GridBagConstraints();
		sendConstraints.gridy = 4;
		sendConstraints.gridwidth = GridBagConstraints.REMAINDER;
		sendConstraints.insets = new Insets( 20, 0, 0, 0 );

		chatScreen.add( sendButton, sendConstraints );

		chatScreen.getRootPane().setDefaultButton( sendButton );
		messageBox.requestFocusInWindow();

	}

	public void showError( String str )
	{
		JOptionPane.showMessageDialog( chatScreen, str );
		chatScreen.dispose();
	}

	public void showUI()
	{
		chatScreen.setVisible( true );
		messageBox.requestFocusInWindow();
	}

	public void updateConversation( String user, String msg )
	{
		if ( !msg.equals( "" ) )
		{
			try
			{
				doc.insertString( doc.getLength(), user + ": ", bold );
				doc.insertString( doc.getLength(), msg + "\n", plain );
			}
			catch ( BadLocationException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			//conversationScroller.scrollRectToVisible( conversationBox.getBounds());
			//conversationBox.setText( conversationBox.getText() + usrText + msg + "\n" );
			showUI();
			conversationScroller.getVerticalScrollBar().setValue( 20);
			conversationBox.setCaretPosition( doc.getLength() );
			}
	}

	public void windowClosed( WindowEvent e )
	{
		chatScreen.setVisible( false );
	}

	public void windowOpened( WindowEvent e )
	{
	}

	public void windowClosing( WindowEvent e )
	{
		chatScreen.setVisible( false );

	}

	public void windowIconified( WindowEvent e )
	{
	}

	public void windowDeiconified( WindowEvent e )
	{
	}

	public void windowActivated( WindowEvent e )
	{
	}

	public void windowDeactivated( WindowEvent e )
	{
	}

	public void actionPerformed( ActionEvent e )
	{
		String curMsg = messageBox.getText();
		updateConversation( ChatClient.hostUser, curMsg );
		messageBox.setText( null );

	}

}
