package server;

import helper.PatternMapper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatServer
{

	public static Selector												acceptSelector;
	private static ServerSocket											srvSocket;
	private static ServerSocketChannel									srvChannel;
	private static ExecutorService										executorService;

	public volatile static ConcurrentHashMap< String, SocketChannel >	userSocketMap	= new ConcurrentHashMap< String, SocketChannel >();

	final public static int												DEFAULT_PORT	= 1000;
	public static int													threadCount;
	final public static Charset											CHARACTER_SET	= Charset.forName( "UTF-8" );
	final public static CharsetEncoder									ENCODER			= CHARACTER_SET.newEncoder();
	final public static CharsetDecoder									DECODER			= CHARACTER_SET.newDecoder();

	final public static String											MSG_CODE_1		= "INIT";
	final public static String											MSG_CODE_2		= "UNAME";
	final public static String											MSG_CODE_3		= "ULIST";
	final public static String											MSG_CODE_4		= "CHAT";
	final public static String											MSG_CODE_5		= "CHATNAME_EXISTS";
	final public static String											MSG_CODE_6		= "USER_ADDITION";
	final public static String											MSG_CODE_7		= "USER_REMOVAL";
	final public static String											MSG_CODE_8		= "SEND_FAILURE";
	final public static String											MSG_CODE_9		= "DISCONNECT";

	final public static String											FAILURE_STRING1	= "Unable to send the message";
	final public static String											FAILURE_STRING2	= "User Exists";

	final public static int												DISCONNECTED	= 0;
	final public static int												CONNECTED		= 1;

	public static volatile ArrayList< SocketChannel >					readSockets		= new ArrayList< SocketChannel >();
	public static ArrayList< SocketChannel >							closeSockets	= new ArrayList< SocketChannel >();

	public static String												serverAddress;
	public static int													serverPort;
	
	final public static String PROPERTIES_FILE = "config/properties.cfg";

	public static void main( String[] args ) throws IOException
	{
		if ( args.length >= 1 )
		{
			if ( !args[ 0 ].matches( "^[0-9]*$" ) )
			{
				System.out.println( "Invalid Port" );
				return;
			}
			serverPort = Integer.parseInt( args[ 0 ] );
		}
		else
			serverPort = ChatServer.DEFAULT_PORT;

		ChatServer chatServer = new ChatServer();
		chatServer.initializeProperties();
		chatServer.startServer();

	}

	private void startServer()
	{
		executorService = Executors.newFixedThreadPool( ChatServer.threadCount );

		try
		{
			srvChannel = ServerSocketChannel.open();
			srvChannel.configureBlocking( false );
			srvSocket = srvChannel.socket();
			InetSocketAddress address = new InetSocketAddress( serverPort );
			srvSocket.bind( address );
		}
		catch ( IOException e )
		{
			System.out.println( "Unable to start the server on port: " + serverPort );
			e.printStackTrace();
		}

		// Thread for listening to incoming client connections
		ConnectionListener conListener = new ConnectionListener();
		( new Thread( conListener ) ).start();

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
		readSockets.remove( ch );
	}

	public static void updateUserStatus( String uName, SocketChannel ch, int status ) throws IOException
	{

		MessageStructure msgStruct;
		ChatServerFactory factory = new ChatServerFactory();
		Set< String > userSet;
		synchronized (uName)
		{
			if ( status == CONNECTED )
			{
				if ( !userSocketMap.containsKey( uName ) && !uName.equals( "" ) )
				{
					synchronized (userSocketMap)
					{
						userSocketMap.put( uName, ch );
						userSet = new HashSet< String >( userSocketMap.keySet() );
					}

					msgStruct = new MessageStructure( ChatServer.MSG_CODE_3, uName, uName, getUserList( userSet ) );
					factory.sendMessage( ch, msgStruct.getMessage() );

					sendUserStatus( uName, userSet, CONNECTED );
				}
				else
				{
					msgStruct = new MessageStructure( ChatServer.MSG_CODE_5, uName, uName, null );
					factory.sendMessage( ch, msgStruct.getMessage() );
				}
			}
			else if ( status == DISCONNECTED )
			{
				if ( userSocketMap.containsKey( uName ) )
				{
					synchronized (userSocketMap)
					{
						userSocketMap.remove( uName );
						userSet = new HashSet< String >( userSocketMap.keySet() );
					}

					sendUserStatus( uName, userSet, DISCONNECTED );
				}

			}
		}
	}

	public static void sendUserStatus( String uName, Set< String > userSet, int status )
	{
		String msgCode;
		if ( status == DISCONNECTED )
		{
			msgCode = ChatServer.MSG_CODE_7;
		}
		else
			msgCode = ChatServer.MSG_CODE_6;

		for ( Iterator< String > it = userSet.iterator(); it.hasNext(); )
		{
			String userName = it.next();

			if ( userName.equals( uName ) )
				continue;

			ChatServerFactory factory = new ChatServerFactory();

			MessageStructure structure = new MessageStructure( msgCode, uName, userName, "" );

			SocketChannel socketChannel = userSocketMap.get( userName );
			try
			{
				factory.sendMessage( socketChannel, structure.getMessage() );
			}
			catch ( IOException e )
			{
				//To be Removed.
			}

		}
	}

	public static SocketChannel getSocket( String uName )
	{
		return userSocketMap.get( uName );
	}

	public static String getUserList( Set< String > userSet )
	{
		Iterator< String > i = userSet.iterator();
		String userList = "";

		while ( i.hasNext() )
			userList = userList + i.next() + "|";

		return userList;

	}

	public static void closeSocket( SocketChannel ch )
	{
		if ( ch != null )

			try
			{
				ch.close();
			}
			catch ( IOException e2 )
			{
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}

		// The values in this list will never be removed!	
		//closeSockets.remove( ch );

	}

	public static void markForClosure( SocketChannel ch )
	{

		if ( !closeSockets.contains( ch ) )
		{
			String uName = null;
			Set< String > userSet = new HashSet< String >( userSocketMap.keySet() );
			Iterator< String > i = userSet.iterator();

			while ( i.hasNext() )
			{
				String user = i.next();
				SocketChannel ch1 = userSocketMap.get( user );

				if ( ch1.equals( ch ) )
				{
					uName = user;
					break;
				}

			}
			try
			{
				if ( uName != null )
					updateUserStatus( uName, ch, DISCONNECTED );
			}
			catch ( IOException e )
			{
				//Exception is suppressed.
			}

			closeSockets.add( ch );
		}

	}

	private class ConnectionListener implements Runnable
	{
		public void run()
		{
			try
			{
				Selector serverSelector = Selector.open();
				srvChannel.register( serverSelector, SelectionKey.OP_ACCEPT );

				while ( true )
				{

					serverSelector.select();

					Set selectedKeys = serverSelector.selectedKeys();
					Iterator iterator = selectedKeys.iterator();

					while ( iterator.hasNext() )
					{
						SelectionKey selectionKey = (SelectionKey)iterator.next();
						SocketChannel channel = null;

						if ( ( selectionKey.readyOps() & SelectionKey.OP_ACCEPT ) == SelectionKey.OP_ACCEPT )
						{
							ServerSocketChannel key = (ServerSocketChannel)selectionKey.channel();

							channel = key.accept();

							channel.configureBlocking( false );
							channel.register( serverSelector, SelectionKey.OP_READ );

							ConnectionInitializer clientCon = new ConnectionInitializer( channel );
							executorService.execute( clientCon );
							checkForClosure( channel );
							iterator.remove();

						}
						else if ( ( selectionKey.readyOps() & SelectionKey.OP_READ ) == SelectionKey.OP_READ )

						{
							channel = (SocketChannel)selectionKey.channel();
							if ( checkForClosure( channel ) )
								continue;
							if ( ChatServer.addSocket( channel ) )
							{
								MessageSplitter messageSplitter = new MessageSplitter( channel );
								executorService.execute( messageSplitter );
							}
							iterator.remove();
						}

					}

				}

			}
			catch ( IOException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		private boolean checkForClosure( SocketChannel ch )
		{
			if ( closeSockets.contains( ch ) )
			{
				closeSocket( ch );
				return true;
			}
			return false;
		}

	}

	class ConnectionInitializer implements Runnable
	{
		private SocketChannel	ch;

		public void run()
		{
			ChatServerFactory factory = new ChatServerFactory();
			MessageStructure msgStructure = new MessageStructure( ChatServer.MSG_CODE_1, null, null, null );
			try
			{
				factory.sendMessage( ch, msgStructure.getMessage() );
			}
			catch ( IOException e )
			{
				markForClosure( ch );
			}

		}

		ConnectionInitializer( SocketChannel s2 )
		{
			this.ch = s2;

		}

	}

	class MessageTransmitter implements Runnable
	{
		private String			inMessage;
		private SocketChannel	socketChannel;

		public void run()
		{

			ChatServerFactory factory = new ChatServerFactory();

			if ( inMessage.equals( "" ) )
				return;

			MessageStructure inMessageStructure = new MessageStructure( inMessage );

			String msgCode = inMessageStructure.getMessageCode();
			String fromUser = inMessageStructure.getFromUser();

			MessageStructure outMessageStructure;

			try
			{
				if ( msgCode.equals( ChatServer.MSG_CODE_2 ) )
				{
					updateUserStatus( fromUser, socketChannel, CONNECTED );
				}
				else if ( msgCode.equals( ChatServer.MSG_CODE_4 ) )
				{
					String toUser = inMessageStructure.getToUser();
					String chat = (String)inMessageStructure.getMessageContent();

					outMessageStructure = new MessageStructure( ChatServer.MSG_CODE_4, fromUser, toUser, chat );
					
					SocketChannel toSocket = ChatServer.getSocket( toUser );
					if ( toSocket != null )
					{
						factory.sendMessage( toSocket, outMessageStructure.getMessage() );
					}
				}

				else if ( msgCode.equals( ChatServer.MSG_CODE_9 ) )
				{
					ChatServer.markForClosure( socketChannel );
				}
			}
			catch ( IOException e )
			{

			}

		}

		MessageTransmitter( SocketChannel ch, String msg )
		{
			this.socketChannel = ch;
			this.inMessage = msg;

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
			CharsetDecoder decoder = CHARACTER_SET.newDecoder();
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
					// TODO Auto-generated catch block
					//e.printStackTrace();
					ChatServer.markForClosure( channel );
					break;
				}
				byteBuf.clear();

			}

			ChatServer.removeSocket( channel );

			ArrayList< String > msgList = splitMessages( msg );
			Iterator< String > msgIterator = msgList.iterator();

			while ( msgIterator.hasNext() )
			{
				String message = msgIterator.next();

				MessageTransmitter msgTransmitter = new MessageTransmitter( channel, message );
				executorService.execute( msgTransmitter );

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
}

class ChatServerFactory
{

	public String readMessage( SocketChannel socketChannel ) throws IOException
	{
		CharsetDecoder decoder = ChatServer.CHARACTER_SET.newDecoder();
		ByteBuffer byteBuf = ByteBuffer.allocate( 1024 );

		String msg = "";

		int r = 0;

		while ( true )
		{
			r = socketChannel.read( byteBuf );
			if ( r <= 0 )
				break;
			byteBuf.flip();
			msg = msg + decoder.decode( byteBuf ).toString();
			byteBuf.clear();

		}

		System.out.println( msg );

		return msg;
	}

	public void sendMessage( SocketChannel ch, String msg ) throws IOException
	{

		CharsetEncoder encoder = ChatServer.CHARACTER_SET.newEncoder();

		CharBuffer charBuf = CharBuffer.wrap( msg );
		ByteBuffer byteBuf = null;
		try
		{
			byteBuf = encoder.encode( charBuf );
		}
		catch ( CharacterCodingException e1 )
		{
			e1.printStackTrace();
		}

		if ( ch != null )
		{
			synchronized (ch)
			{
				ch.write( byteBuf );
			}
		}

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
