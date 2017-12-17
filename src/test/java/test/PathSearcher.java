package test;

import java.io.File;

/**
 * @author VISTALL
 * @since 17-Dec-17
 */
public class PathSearcher
{
	public static File getTestPath(String path) throws Exception
	{
		String rootPath = PathSearcher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();

		return new File(rootPath, path);
	}
}
