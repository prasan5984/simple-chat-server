package client;

import helper.PatternMapper;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class ChatClient
{
	public static String								hostUser;
	private static HashMap< String, ChatScreen >		openedChats			= new HashMap< String, ChatScreen >();
	private static ArrayList< String >					openChats			= new ArrayList< String >();
	public static DefaultListModel						userListModel		= new DefaultListModel();
	private static ArrayList< String >					peerUsersList		= new ArrayList< String >();

	public static String								serverAddress;
	public static int									serverPort;

	final public static int								DEFAULT_PORT		= 1000;

	public static ChatClientFactory						factory				= new ChatClientFactory();

	final public static String							MSG_CODE_1			= "INIT";
	final public static String							MSG_CODE_2			= "UNAME";
	final public static String							MSG_CODE_3			= "ULIST";
	final public static String							MSG_CODE_4			= "CHAT";
	final public static String							MSG_CODE_5			= "CHATNAME_EXISTS";
	final public static String							MSG_CODE_6			= "USER_ADDITION";
	final public static String							MSG_CODE_7			= "USER_REMOVAL";
	final public static String							MSG_CODE_8			= "SEND_FAILURE";			
	final public static String							MSG_CODE_9			= "DISCONNECT";

	final public static String							FAILURE_STRING1		= "Unable to send the message";
	final public static String							FAILURE_STRING2		= "User Exists";

	final public static Charset							CHARACTER_SET		= Charset.forName( "UTF-8" );
	final public static CharsetEncoder					ENCODER				= CHARACTER_SET.newEncoder();
	final public static CharsetDecoder					DECODER				= CHARACTER_SET.newDecoder();

	public static ExecutorService						executorService;

	public static int									threadCount;
	private static SocketChannel						clientSocket		= null;

	private static LoginScreen							loginScreen;
	private static ContactsScreen						contactsScreen;

	public static final int								ADDITION			= 1;
	public static final int								REMOVAL				= 0;

	private static volatile ArrayList< SocketChannel >	readSockets			= new ArrayList< SocketChannel >();

	public static Screens								curActiveScreen;

	public static boolean								appStatus			= true;

	//Fonts
	public final static Font							FONT_L1				= new Font( "Times New Roman", Font.PLAIN, 12 );
	public final static Font							FONT_L2				= new Font( "Times New Roman", Font.BOLD, 12 );
	public final static Font							FONT_L10			= new Font( "Edwardian Script ITC", Font.BOLD, 25 );
	
	//Color
	public final static Color							BACKGROUND_COLOR	= Color.getHSBColor( 0.6f, 0.05f, .98f );
	
	final public static String PROPERTIES_FILE = "config/properties.cfg";

	public static void main( String[] args ) throws FileNotFoundException, IOException
	{
		ChatClient client = new ChatClient();
		client.initializeProperties();
		client.start();
	}

	public void initializeProperties() throws FileNotFoundException, IOException
	{
		Map< String, String > propMap = null;
		propMap = PatternMapper.patternAtStart( PROPERTIES_FILE, new String[] { "THREAD_COUNT" } );

		String prop = propMap.get( "THREAD_COUNT" );

		if ( prop.matches( "^[0-9]*$" ) )
			threadCount = Integer.parseInt( prop );
		else
		{
			System.out.println( "Invalid Port specified." );
			System.exit( 0 );
		}

	}

	public void start()
	{
		executorService = Executors.newFixedThreadPool( threadCount );

		LoginScreen loginScreen = new LoginScreen();
		loginScreen.createScreen();
		curActiveScreen = loginScreen;

	}

	public static ExecutorService getExecutor()
	{
		return executorService;
	}

	public static boolean addSocket( SocketChannel ch )
	{
		if ( !readSockets.contains( ch ) )
		{
			readSockets.add( ch );
			return true;
		}
		else
		{
			return false;
		}

	}

	public static void removeSocket( SocketChannel ch )
	{
		if ( readSockets.contains( ch ) )
			readSockets.remove( ch );

	}

	public static void setListModel( ArrayList< String > usrList )
	{
		Collections.sort( usrList );
		if ( usrList.contains( hostUser ) )
			usrList.remove( hostUser );
		peerUsersList = usrList;

		for ( int i = 0; i < usrList.size(); i++ )
		{
			userListModel.addElement( usrList.get( i ) );

		}

	}

	public static void addPeerUser( String peerUsr )
	{
		synchronized (peerUsersList)
		{

			if ( !peerUsersList.contains( peerUsr ) && !userListModel.contains( peerUsr ) )
			{
				peerUsersList.add( peerUsr );
				Collections.sort( peerUsersList );
				int index = peerUsersList.indexOf( peerUsr );
				userListModel.add( index, peerUsr );
			}
		}

	}

	public static void removePeerUser( String peerUsr )
	{
		if ( userListModel.contains( peerUsr ) && peerUsersList.contains( peerUsr ) )
		{
			synchronized (peerUsersList)
			{
				peerUsersList.remove( peerUsr );
				userListModel.removeElement( peerUsr );
			}
			if ( openedChats.containsKey( peerUsr ) )
			{
				if ( openChats.contains( peerUsr ) )
					getChatWindow( peerUsr ).showError( peerUsr + " has disconnected." );
				synchronized (peerUsr)
				{
					openedChats.remove( peerUsr );
				}
			}
		}
	}

	public static boolean checkUserName( String usr )
	{
		hostUser = usr;
		return true;
	}

	public static ChatScreen getChatWindow( String usr )
	{
		ChatScreen chtScr;

		if ( ( openedChats.containsKey( usr ) ) )
			chtScr = openedChats.get( usr );

		else
		{
			chtScr = new ChatScreen( usr );
			chtScr.createScreen();
			synchronized (usr)
			{
				openedChats.put( usr, chtScr );
			}
		}

		return chtScr;

	}

	public static void setLoginScreen( LoginScreen loginScreen )
	{
		ChatClient.loginScreen = loginScreen;
	}

	public static LoginScreen getLoginScreen()
	{
		return loginScreen;
	}

	public static void updateOpenChats( String usr, int status )
	{
		synchronized (usr)
		{
			if ( ( openChats.contains( usr ) ) && ( status == ChatClient.REMOVAL ) )
				openChats.remove( usr );
			if ( ( !openChats.contains( usr ) ) && ( status == ChatClient.ADDITION ) )
				openChats.add( usr );
		}

	}

	public static void closeConnection()
	{

		if ( clientSocket != null & clientSocket.isOpen() )

			try
			{
				clientSocket.close();
			}
			catch ( IOException e2 )
			{
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			finally
			{
				appStatus = false;
			}

	}

	public static void exitApplication()
	{
		if ( clientSocket != null )
		{

			ChatClientFactory factory = new ChatClientFactory();
			MessageStructure msgStructure = new MessageStructure( ChatClient.MSG_CODE_9, ChatClient.hostUser, ChatClient.hostUser, null );

			boolean status = clientSocket.isOpen();

			if ( status )
			{
				try
				{
					factory.sendMessage( clientSocket, msgStructure.getMessage() );
				}
				catch ( IOException e )
				{
					// TODO Auto-generated catch block
					//e.printStackTrace();
				}
				closeConnection();
			}
		}
	}

	public static void setClientSocket( SocketChannel clientSocket )
	{
		ChatClient.clientSocket = clientSocket;
	}

	public static SocketChannel getClientSocket()
	{
		return clientSocket;
	}

	public static void setContactsScreen( ContactsScreen contactsScreen )
	{
		ChatClient.contactsScreen = contactsScreen;
	}

	public static ContactsScreen getContactsScreen()
	{
		return contactsScreen;
	}

}

class LoginScreen implements ActionListener, WindowListener, Screens
{

	JFrame				logScreen;
	JTextField			userName, serverName;
	ChatClientFactory	factory	= new ChatClientFactory();
	Socket				s;
	String				uName;
	SocketChannel		ch		= null;

	public void createScreen()
	{

		GridBagLayout loginLayout = new GridBagLayout();

		logScreen = new JFrame( "Chat App" );
		logScreen.setLayout( loginLayout );
		logScreen.setSize( 300, 200 );
		logScreen.setResizable( false );
		logScreen.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE );
		logScreen.setLocationRelativeTo( null );

		logScreen.getContentPane().setBackground( ChatClient.BACKGROUND_COLOR );

		logScreen.addWindowListener( this );

		JLabel heading = new JLabel( "Welcome to Chat App!" );
		heading.setFont( ChatClient.FONT_L10 );

		JLabel userLabel = new JLabel( "Chat Name:" );
		userLabel.setFont( ChatClient.FONT_L2 );

		userName = new JTextField( 12 );
		userName.setFont( ChatClient.FONT_L1 );

		JLabel serverLabel = new JLabel( "Machine:" );
		serverLabel.setFont( ChatClient.FONT_L2 );

		serverName = new JTextField( 12 );
		serverName.setFont( ChatClient.FONT_L1 );

		JButton loginButton = new JButton( "Login" );
		loginButton.setFont( ChatClient.FONT_L2 );

		loginButton.addActionListener( this );

		logScreen.getRootPane().setDefaultButton( loginButton );

		Container cp = logScreen.getContentPane();

		GridBagConstraints headConstraint = new GridBagConstraints();
		headConstraint.gridwidth = GridBagConstraints.REMAINDER;
		headConstraint.gridy = 0;
		cp.add( heading, headConstraint );

		GridBagConstraints labelConstraint = new GridBagConstraints();
		labelConstraint.insets = new Insets( 23, 0, 0, 0 );
		labelConstraint.gridy = 1;
		cp.add( userLabel, labelConstraint );

		GridBagConstraints textConstraint = new GridBagConstraints();
		textConstraint.insets = new Insets( 24, 10, 0, 0 );
		textConstraint.gridy = 1;
		cp.add( userName, textConstraint );

		GridBagConstraints serverLabelConstraint = new GridBagConstraints();
		serverLabelConstraint.insets = new Insets( 11, 0, 0, 0 );
		serverLabelConstraint.gridy = 2;
		cp.add( serverLabel, serverLabelConstraint );

		GridBagConstraints serverNameConstraint = new GridBagConstraints();
		serverNameConstraint.insets = new Insets( 12, 10, 0, 0 );
		serverNameConstraint.gridy = 2;
		cp.add( serverName, serverNameConstraint );

		GridBagConstraints buttonConstraint = new GridBagConstraints();
		buttonConstraint.gridy = 3;
		//buttonConstraint.gridx = 1;
		buttonConstraint.gridwidth = GridBagConstraints.REMAINDER;
		buttonConstraint.insets = new Insets( 17, 0, 0, 0 );
		cp.add( loginButton, buttonConstraint );

		logScreen.setVisible( true );

	}

	public void bridgeServerConnection()
	{

		try
		{
			this.ch = SocketChannel.open();
			Socket clientSocket = ch.socket();
			SocketAddress address = new InetSocketAddress( ChatClient.serverAddress, ChatClient.serverPort );
			clientSocket.connect( address, 100 );
		}
		catch ( IOException e )
		{
			ChatClient.getLoginScreen().showDialog(
					"Could not connect to the server. Check if server is running in " + ChatClient.serverAddress + ":" + ChatClient.serverPort );
		}

		ChatClient.setClientSocket( ch );

		MessageReceiver receiver = new MessageReceiver( ch );
		ChatClient.executorService.execute( receiver );
	}

	public void sendUserName()
	{
		MessageStructure outMsgStructure = new MessageStructure( ChatClient.MSG_CODE_2, this.uName, this.uName, null );
		try
		{
			factory.sendMessage( this.ch, outMsgStructure.getMessage() );
		}
		catch ( IOException e )
		{
			ChatClient.curActiveScreen.showError( "Server connection lost. Please login again." );
		}

	}

	public void createContactScreen( Object msgText )
	{
		ChatClient.checkUserName( this.uName );
		String users = (String)msgText;

		String[] arr = users.split( Pattern.quote( "|" ) );
		ArrayList< String > userList = new ArrayList< String >( Arrays.asList( arr ) );

		ChatClient.setListModel( userList );

		logScreen.dispose();
		ContactsScreen contactScreen = new ContactsScreen();
		contactScreen.createScreen();
		ChatClient.setContactsScreen( contactScreen );
		ChatClient.curActiveScreen = contactScreen;
	}

	public void showDialog( String str )
	{
		JOptionPane.showMessageDialog( logScreen, str );
		logScreen.setEnabled( true );
		logScreen.setVisible( true );
	}

	public void showError( String str )
	{
		JOptionPane.showMessageDialog( logScreen, str );
		logScreen.dispatchEvent( new WindowEvent( logScreen, WindowEvent.WINDOW_CLOSING ) );
	}

	public void actionPerformed( ActionEvent e )
	{
		logScreen.setEnabled( false );
		String[] serverDetails = serverName.getText().split( ":" );

		if ( userName.getText().trim().equals( "" ) )
		{
			showDialog( "Chat Name cannot be empty" );
			return;
		}
		else if ( userName.getText().contains( ":" ) )
		{
			showDialog( "Chat Name cannot contain colon" );
			return;
		}
		else if ( serverName.getText().trim().equals( "" ) )
		{
			showDialog( "Server Details cannot be empty" );
			return;
		}

		else if ( serverDetails.length == 2 && !serverDetails[ 1 ].matches( "^[0-9]*$" ) )
		{
			showDialog( "Invalid Port" );
			return;
		}

		this.uName = userName.getText();

		ChatClient.serverAddress = serverDetails[ 0 ];

		if ( serverDetails.length == 2 )
			ChatClient.serverPort = Integer.parseInt( serverDetails[ 1 ] );
		else
			ChatClient.serverPort = ChatClient.DEFAULT_PORT;

		ChatClient.setLoginScreen( this );
		bridgeServerConnection();
	}

	public void windowOpened( WindowEvent e )
	{
	}

	public void windowClosing( WindowEvent e )
	{
	}

	public void windowClosed( WindowEvent e )
	{
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

}

class ContactsScreen implements MouseListener, WindowListener, Screens, ActionListener
{
	JList				contactList;
	DefaultListModel	listModel;
	JFrame				contactsScreen;
	JTextPane conversationBox;
	StyledDocument doc;
	JEditorPane					messageBox;
	private MessageStructure	chatStructure;
	ChatClientFactory			factory	= new ChatClientFactory();
	javax.swing.text.Style		bold, plain;
	JButton						sendButton;
	JScrollPane conversationScroller;

	public void createScreen()
	{

		GridBagLayout loginLayout = new GridBagLayout();

		contactsScreen = new JFrame( ChatClient.hostUser );
		contactsScreen.setLayout( loginLayout );
		contactsScreen.setSize( 800, 600 );
		contactsScreen.setResizable( false );
		contactsScreen.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE );
		contactsScreen.setLocationRelativeTo( null );

		contactsScreen.addWindowListener( this );

		JLabel heading = new JLabel( "Online Users" );
		heading.setFont( ChatClient.FONT_L2 );

		listModel = ChatClient.userListModel;

		contactList = new JList( listModel );
		contactList.setFont( ChatClient.FONT_L1 );
		contactList.addMouseListener( this );

		JScrollPane listScroller = new JScrollPane( contactList );
		listScroller.setPreferredSize( new Dimension( 140, 350 ) );
		//contactList.setLayout( null );

		Container contentPane = contactsScreen.getContentPane();

		GridBagConstraints headingConstraint = new GridBagConstraints();
		//headingConstraint.gridwidth = GridBagConstraints.REMAINDER;
		headingConstraint.anchor = GridBagConstraints.NORTHWEST;
		contentPane.add( heading, headingConstraint );

		GridBagConstraints listConstraint = new GridBagConstraints();
		listConstraint.gridy = 1;
		listConstraint.insets = new Insets( 15, 0, 0, 0 );
		contentPane.add( listScroller, listConstraint );

		contentPane.setBackground( ChatClient.BACKGROUND_COLOR );
		//contactsScreen.getContentPane().add( heading );
		//contactsScreen.getContentPane().add( listScroller );

		
		//Public Chat
		
		JLabel chatHistory = new JLabel( "Chat History" );
		chatHistory.setFont( ChatClient.FONT_L2 );

		GridBagConstraints historyContraints = new GridBagConstraints();
		historyContraints.gridx = 1;
		historyContraints.anchor = GridBagConstraints.NORTHWEST;
		historyContraints.insets = new Insets( 0, 20, 0, 0 );
		contactsScreen.add( chatHistory, historyContraints );

		// Conversation 

		JLabel conversation = new JLabel( "Type your message below:" );
		conversation.setFont( ChatClient.FONT_L2 );

		GridBagConstraints conversationConstraint = new GridBagConstraints();
		conversationConstraint.gridx = 1;
		conversationConstraint.gridy = 2;
		conversationConstraint.anchor = GridBagConstraints.NORTHWEST;
		conversationConstraint.insets = new Insets( 20, 20, 0, 0 );
		contactsScreen.add( conversation, conversationConstraint );

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
		conversationConstraints.gridx = 1;
		conversationConstraints.gridy = 1;
		conversationConstraints.insets = new Insets( 10, 20, 0, 0 );

		contactsScreen.add( conversationScroller, conversationConstraints );

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
		messageConstraints.gridx = 1;
		messageConstraints.gridy = 3;
		messageConstraints.insets = new Insets( 10, 20, 0, 0 );

		contactsScreen.add( messageScroller, messageConstraints );

		// Send Button

		sendButton = new JButton( "Send" );
		sendButton.setFont( ChatClient.FONT_L2 );
		sendButton.addActionListener( this );

		GridBagConstraints sendConstraints = new GridBagConstraints();
		sendConstraints.gridx = 1;
		sendConstraints.gridy = 4;
		sendConstraints.gridwidth = GridBagConstraints.REMAINDER;
		sendConstraints.insets = new Insets( 20, 20, 0, 0 );

		contactsScreen.add( sendButton, sendConstraints );

		contactsScreen.getRootPane().setDefaultButton( sendButton );
		messageBox.requestFocusInWindow();
		contactsScreen.setVisible( true );

	}

	public void mouseClicked( MouseEvent e )
	{
		if ( e.getClickCount() == 2 )
		{
			String usrClicked = (String)contactList.getSelectedValue();
			if ( !usrClicked.isEmpty() )
			{
				ChatScreen scr = ChatClient.getChatWindow( (String)contactList.getSelectedValue() );
				scr.showUI();
			}
		}

	}

	public void showError( String str )
	{
		JOptionPane.showMessageDialog( contactsScreen, str );
		contactsScreen.dispatchEvent( new WindowEvent( contactsScreen, WindowEvent.WINDOW_CLOSING ) );
	}

	public void mousePressed( MouseEvent e )
	{
	}

	public void mouseReleased( MouseEvent e )
	{
	}

	public void mouseEntered( MouseEvent e )
	{
	}

	public void mouseExited( MouseEvent e )
	{
	}

	public void windowOpened( WindowEvent e )
	{
	}

	public void windowClosing( WindowEvent e )
	{
		ChatClient.exitApplication();
	}

	public void windowClosed( WindowEvent e )
	{
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

	@Override
	public void actionPerformed( ActionEvent e )
	{
		// TODO Auto-generated method stub
		
	}

}

class ChatScreen implements ActionListener, WindowListener
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
	JScrollPane conversationScroller;

	public ChatScreen( String usr )
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
		ChatClient.updateOpenChats( user, ChatClient.ADDITION );
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
			conversationBox.setCaretPosition( doc.getLength() );
		}
	}

	public void windowClosed( WindowEvent e )
	{
		chatScreen.setVisible( false );
		ChatClient.updateOpenChats( user, ChatClient.REMOVAL );

	}

	public void windowOpened( WindowEvent e )
	{
	}

	public void windowClosing( WindowEvent e )
	{
		chatScreen.setVisible( false );
		ChatClient.updateOpenChats( user, ChatClient.REMOVAL );
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

		chatStructure.setMessageContent( curMsg );
		try
		{
			factory.sendMessage( ChatClient.getLoginScreen().ch, chatStructure.getMessage() );
		}
		catch ( IOException e1 )
		{
			//ChatClient.closeWindow( ChatClient.curActiveScreen );
			ChatClient.curActiveScreen.showError( "Server connection lost. Unable to send the message" );
		}

	}

}

class ChatClientFactory
{
	public void sendMessage( SocketChannel ch, String msg ) throws IOException
	{
		CharsetEncoder encoder = ChatClient.CHARACTER_SET.newEncoder();
		CharBuffer charBuf = CharBuffer.wrap( msg );
		ByteBuffer byteBuf;
		try
		{
			byteBuf = encoder.encode( charBuf );
		}
		catch ( CharacterCodingException e1 )
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}

		ch.write( byteBuf );
	}

}

class MessageReceiver implements Runnable
{

	SocketChannel	channel;

	public MessageReceiver( SocketChannel ch )
	{
		this.channel = ch;
	}

	public void run()
	{

		try
		{
			Selector readSelector = Selector.open();
			channel.configureBlocking( false );
			channel.register( readSelector, SelectionKey.OP_READ );

			while ( true )
			{
				readSelector.select();

				if ( !ChatClient.appStatus )
				{
					readSelector.close();
					return;
				}

				Set msgSets = readSelector.selectedKeys();
				Iterator iterator = msgSets.iterator();

				while ( iterator.hasNext() )
				{
					SocketChannel ch = (SocketChannel)( (SelectionKey)iterator.next() ).channel();

					if ( ChatClient.addSocket( ch ) )
					{
						MessageSplitter messageSplitter = new MessageSplitter( ch );
						ChatClient.getExecutor().execute( messageSplitter );
					}
					iterator.remove();

				}

			}
		}
		catch ( IOException e )
		{
			ChatClient.curActiveScreen.showError( "Server connection lost. Please login again." );
		}
	}
}

class MessageSplitter implements Runnable
{

	private SocketChannel	channel;

	public MessageSplitter( SocketChannel ch )
	{
		this.channel = ch;

	}

	public void run()
	{
		CharsetDecoder decoder = ChatClient.CHARACTER_SET.newDecoder();
		ByteBuffer byteBuf = ByteBuffer.allocate( 1024 );

		String msg = "";

		int r;

		while ( true )
		{

			try
			{
				r = channel.read( byteBuf );
				if ( r <= 0 )
					break;
				byteBuf.flip();
				msg = msg + decoder.decode( byteBuf ).toString();
			}

			catch ( CharacterCodingException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
				break;
			}
			catch ( IOException e )
			{
				ChatClient.curActiveScreen.showError( "Server connection lost. Please login again" );
				break;
			}
			byteBuf.clear();

		}

		ChatClient.removeSocket( channel );

		ArrayList< String > msgList = splitMessages( msg );
		Iterator< String > msgIterator = msgList.iterator();

		while ( msgIterator.hasNext() )
		{
			MessageProcessor msgProcessor = new MessageProcessor( msgIterator.next() );

			ChatClient.getExecutor().execute( msgProcessor );

		}

	}

	private ArrayList< String > splitMessages( String str )
	{
		ArrayList< String > msgList = new ArrayList< String >();

		Pattern pattern = Pattern.compile( ",?(.*?):" );
		Matcher matcher = pattern.matcher( str );

		int i = 0;
		int j;

		while ( matcher.find( i ) )
		{
			j = i;
			i = matcher.end() + Integer.parseInt( matcher.group( 1 ) );
			msgList.add( str.substring( j, i ) );
		}

		return msgList;

	}
}

class MessageProcessor implements Runnable
{
	static LoginScreen	loginScreen	= ChatClient.getLoginScreen();
	String				message;
	ChatClientFactory	factory		= new ChatClientFactory();

	public MessageProcessor( String msg )
	{
		this.message = msg;
	}

	public void run()
	{
		MessageStructure msgStructure = new MessageStructure( this.message );

		String messageCode = msgStructure.getMessageCode();
		Object messageText = msgStructure.getMessageContent();
		String fromUser = msgStructure.getFromUser();

		if ( messageCode.equals( ChatClient.MSG_CODE_4 ) )
		{
			ChatScreen scr = ChatClient.getChatWindow( fromUser );
			scr.updateConversation( fromUser, (String)messageText );

		}
		else if ( messageCode.equals( ChatClient.MSG_CODE_6 ) )
			ChatClient.addPeerUser( fromUser );
		else if ( messageCode.equals( ChatClient.MSG_CODE_7 ) )
			ChatClient.removePeerUser( fromUser );
		else if ( messageCode.equals( ChatClient.MSG_CODE_1 ) )
			loginScreen.sendUserName();
		else if ( messageCode.equals( ChatClient.MSG_CODE_3 ) )
			loginScreen.createContactScreen( messageText );
		else if ( messageCode.equals( ChatClient.MSG_CODE_5 ) )
			loginScreen.showDialog( "Chat Name not available. Please use a different Chat Name" );
	}
}

class MessageStructure
{

	private String	message, fromUser, toUser, messageCode;
	private Object	messageContent;
	private String	seperator	= ":";
	private int		messageLength;

	public MessageStructure( String msg )
	{
		this.message = msg;
		mapMessage();
	}

	public MessageStructure( String mCode, String fUser, String tUser, Object mContent )
	{
		this.fromUser = fUser;
		this.toUser = tUser;
		this.messageCode = mCode;
		this.messageContent = mContent;
		formMessage();
	}

	private void formMessage()
	{

		this.message = this.messageCode + seperator + this.fromUser + seperator + this.toUser + seperator + this.getMessageContent();
		this.messageLength = message.length();
		this.message = this.messageLength + seperator + message;

	}

	private void mapMessage()
	{

		Pattern pattern = Pattern.compile( "(.*?):" );
		Matcher matcher = pattern.matcher( message );

		int i = 0;
		int j = 0;
		String arr[] = new String[5];

		while ( matcher.find( i ) && j <= 3 )
		{

			i = matcher.end();
			arr[ j ] = matcher.group( 1 );
			j++;
			if ( j == 4 )
			{
				arr[ j ] = message.substring( matcher.end() );
			}
		}

		this.messageLength = Integer.parseInt( arr[ 0 ] );
		this.messageCode = arr[ 1 ];
		this.fromUser = arr[ 2 ];
		this.toUser = arr[ 3 ];
		this.messageContent = arr[ 4 ];

	}

	public void setMessageContent( Object msgContent )
	{
		this.messageContent = msgContent;
		formMessage();
	}

	public int getMessageLength()
	{
		return messageLength;
	}

	public Object getMessageContent()
	{
		return messageContent;
	}

	public String getMessage()
	{
		return message;
	}

	public String getFromUser()
	{
		return fromUser;
	}

	public String getToUser()
	{
		return toUser;
	}

	public String getMessageCode()
	{
		return messageCode;
	}

}

interface Screens
{
	public void showError( String str );
}