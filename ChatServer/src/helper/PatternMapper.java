package helper;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class PatternMapper
{
	public static Map< String, String > patternAtStart( String filename, String patternArray[] )throws FileNotFoundException, IOException 
	{
		Map< String, String > dbProp = new HashMap< String, String >();

		BufferedReader reader = new BufferedReader( new FileReader( filename ) );
		String line = null;
		while ( ( line = reader.readLine() ) != null )
		{
			for ( String pat : patternArray )
			{
				if ( line.matches( "(?i)^" + pat + ":.*" ) )
				{
					dbProp.put( pat, line.replaceFirst( "(?i)^" + pat + ":", "" ) );
				}
			}

		}
		return dbProp;
	}
}
