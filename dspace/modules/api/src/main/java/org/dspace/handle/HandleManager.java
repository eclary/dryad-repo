/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.identifier.IdentifierNotFoundException;
import org.dspace.identifier.IdentifierNotResolvableException;
import org.dspace.identifier.IdentifierService;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;
import org.dspace.utils.DSpace;

/**
 * Interface to the <a href="http://www.handle.net" target=_new>CNRI Handle
 * System </a>.
 * 
 * <p>
 * Currently, this class simply maps handles to local facilities; handles which
 * are owned by other sites (including other DSpaces) are treated as
 * non-existent.
 * </p>
 * 
 * @author Peter Breton
 * @version $Revision$
 */
public class HandleManager
{
    /** log4j category */
    private static Logger log = Logger.getLogger(HandleManager.class);

    /** Prefix registered to no one */
    static final String EXAMPLE_PREFIX = "123456789";

    /** Private Constructor */
    private HandleManager()
    {
    }

    /**
     * Return the local URL for handle, or null if handle cannot be found.
     * 
     * The returned URL is a (non-handle-based) location where a dissemination
     * of the object referred to by handle can be obtained.
     * 
     * @param context
     *            DSpace context
     * @param handle
     *            The handle
     * @return The local URL
     * @exception SQLException
     *                If a database error occurs
     */
    public static String resolveToURL(Context context, String handle)
            throws SQLException
    {
        TableRow dbhandle = findHandleInternal(context, handle);

        if (dbhandle == null)
        {
            return null;
        }

        String url = ConfigurationManager.getProperty("dspace.url")
                + "/handle/" + handle;

        if (log.isDebugEnabled())
        {
            log.debug("Resolved " + handle + " to " + url);
        }

        return url;
    }

    /**
     * Transforms handle into the canonical form <em>hdl:handle</em>.
     * 
     * No attempt is made to verify that handle is in fact valid.
     * 
     * @param handle
     *            The handle
     * @return The canonical form
     */
    public static String getCanonicalForm(String handle)
    {
    	
    	// Let the admin define a new prefix, if not then we'll use the 
    	// CNRI default. This allows the admin to use "hdl:" if they want too or
    	// use a locally branded prefix handle.myuni.edu.
    	String handlePrefix = ConfigurationManager.getProperty("handle.canonical.prefix");
    	if (handlePrefix == null || handlePrefix.length() == 0)
    	{
    		handlePrefix = "http://hdl.handle.net/";
    	}
    	
    	return handlePrefix + handle;
    }

    /**
     * Returns displayable string of the handle's 'temporary' URL
     * <em>http://hdl.handle.net/handle/em>.
     *
     * No attempt is made to verify that handle is in fact valid.
     *
     * @param handle The handle
     * @return The canonical form
     */

    //    public static String getURLForm(String handle)
    //    {
    //        return "http://hdl.handle.net/" + handle;
    //    }

    /**
     * Creates a new handle in the database.
     * 
     * @param context
     *            DSpace context
     * @param dso
     *            The DSpaceObject to create a handle for
     * @return The newly created handle
     * @exception SQLException
     *                If a database error occurs
     */
    public static String createHandle(Context context, DSpaceObject dso)
            throws SQLException
    {
        TableRow handle = DatabaseManager.create(context, "Handle");
        String handleId = createId(handle.getIntColumn("handle_id"));

        handle.setColumn("handle", handleId);
        handle.setColumn("resource_type_id", dso.getType());
        handle.setColumn("resource_id", dso.getID());
        DatabaseManager.update(context, handle);

        if (log.isDebugEnabled())
        {
            log.debug("Created new handle for "
                    + Constants.typeText[dso.getType()] + " (ID=" + dso.getID() + ") " + handleId );
        }

        return handleId;
    }

    /**
     * Creates a handle entry, but with a handle supplied by the caller (new
     * Handle not generated)
     * 
     * @param context
     *            DSpace context
     * @param dso
     *            DSpaceObject
     * @param suppliedHandle
     *            existing handle value
     * @return the Handle
     * @throws IllegalStateException if specified handle is already in use by another object
     */
    public static String createHandle(Context context, DSpaceObject dso,
            String suppliedHandle) throws SQLException, IllegalStateException
    {
        //Check if the supplied handle is already in use -- cannot use the same handle twice
        TableRow handle = findHandleInternal(context, suppliedHandle);
        if(handle!=null && !handle.isColumnNull("resource_id"))
        {
            //Check if this handle is already linked up to this specified DSpace Object
            if(handle.getIntColumn("resource_id")==dso.getID() &&
               handle.getIntColumn("resource_type_id")==dso.getType())
            {
                //This handle already links to this DSpace Object -- so, there's nothing else we need to do
                return suppliedHandle;
            }
            else
            {
                //handle found in DB table & already in use by another existing resource
                throw new IllegalStateException("Attempted to create a handle which is already in use: " + suppliedHandle);
            }
        }
        else if(handle!=null && !handle.isColumnNull("resource_type_id"))
        {
            //If there is a 'resource_type_id' (but 'resource_id' is empty), then the object using
            // this handle was previously unbound (see unbindHandle() method) -- likely because object was deleted
            int previousType = handle.getIntColumn("resource_type_id");

            //Since we are restoring an object to a pre-existing handle, double check we are restoring the same *type* of object
            // (e.g. we will not allow an Item to be restored to a handle previously used by a Collection)
            if(previousType != dso.getType())
            {
                throw new IllegalStateException("Attempted to reuse a handle previously used by a " +
                        Constants.typeText[previousType] + " for a new " +
                        Constants.typeText[dso.getType()]);
            }
        }
        else if(handle==null) //if handle not found, create it
        {
            //handle not found in DB table -- create a new table entry
            handle = DatabaseManager.create(context, "Handle");
            handle.setColumn("handle", suppliedHandle);
        }

        handle.setColumn("resource_type_id", dso.getType());
        handle.setColumn("resource_id", dso.getID());
        DatabaseManager.update(context, handle);

        if (log.isDebugEnabled())
        {
            log.debug("Created new handle for "
                    + Constants.typeText[dso.getType()] + " (ID=" + dso.getID() + ") " + suppliedHandle );
        }

        return suppliedHandle;
    }

    /**
     * Removes binding of Handle to a DSpace object, while leaving the
     * Handle in the table so it doesn't get reallocated.  The AIP
     * implementation also needs it there for foreign key references.
     *
     * @param context DSpace context
     * @param dso DSpaceObject whose Handle to unbind.
     */
    public static void unbindHandle(Context context, DSpaceObject dso)
        throws SQLException
    {
        TableRow row = getHandleInternal(context, dso.getType(), dso.getID());
        if (row != null)
        {
            //Only set the "resouce_id" column to null when unbinding a handle.
            // We want to keep around the "resource_type_id" value, so that we
            // can verify during a restore whether the same *type* of resource
            // is reusing this handle!
            row.setColumnNull("resource_id");
            DatabaseManager.update(context, row);

            if(log.isDebugEnabled())
            {
                log.debug("Unbound Handle " + row.getStringColumn("handle") + " from object " + Constants.typeText[dso.getType()] + " id=" + dso.getID());
            }

        }
        else
        {
            log.warn("Cannot find Handle entry to unbind for object " + Constants.typeText[dso.getType()] + " id=" + dso.getID());
        }
    }

    /**
     * Return the object which handle maps to, or null. This is the object
     * itself, not a URL which points to it.
     * 
     * @param context
     *            DSpace context
     * @param handle
     *            The handle to resolve
     * @return The object which handle maps to, or null if handle is not mapped
     *         to any object.
     * @exception IllegalStateException
     *                If handle was found but is not bound to an object
     * @exception SQLException
     *                If a database error occurs
     */
    public static DSpaceObject resolveToObject(Context context, String handle)
            throws IllegalStateException, SQLException
    {
        TableRow dbhandle = findHandleInternal(context, handle);

        if (dbhandle == null)
        {
            //If this is the Site-wide Handle, return Site object
            if (handle.equals(Site.getSiteHandle()))
            {
                return Site.find(context, 0);
            }
            //Otherwise, return null (i.e. handle not found in DB)
            return null;
        }

        // check if handle was allocated previously, but is currently not
        // associated with a DSpaceObject 
        // (this may occur when 'unbindHandle()' is called for an obj that was removed)
        if ((dbhandle.isColumnNull("resource_type_id"))
                || (dbhandle.isColumnNull("resource_id")))
        {
            //if handle has been unbound, just return null (as this will result in a PageNotFound)
            return null;
        }

        // What are we looking at here?
        int handletypeid = dbhandle.getIntColumn("resource_type_id");
        int resourceID = dbhandle.getIntColumn("resource_id");

        if (handletypeid == Constants.ITEM)
        {
            Item item = Item.find(context, resourceID);

            if (log.isDebugEnabled())
            {
                log.debug("Resolved handle " + handle + " to item "
                        + ((item == null) ? (-1) : item.getID()));
            }

            return item;
        }
        else if (handletypeid == Constants.COLLECTION)
        {
            Collection collection = Collection.find(context, resourceID);

            if (log.isDebugEnabled())
            {
                log.debug("Resolved handle " + handle + " to collection "
                        + ((collection == null) ? (-1) : collection.getID()));
            }

            return collection;
        }
        else if (handletypeid == Constants.COMMUNITY)
        {
            Community community = Community.find(context, resourceID);

            if (log.isDebugEnabled())
            {
                log.debug("Resolved handle " + handle + " to community "
                        + ((community == null) ? (-1) : community.getID()));
            }

            return community;
        }

        throw new IllegalStateException("Unsupported Handle Type "
                + Constants.typeText[handletypeid]);
    }

    /**
     * Return the handle for an Object, or null if the Object has no handle.
     * 
     * @param context
     *            DSpace context
     * @param dso
     *            The object to obtain a handle for
     * @return The handle for object, or null if the object has no handle.
     * @exception SQLException
     *                If a database error occurs
     */
    public static String findHandle(Context context, DSpaceObject dso)
            throws SQLException
    {
        TableRow row = getHandleInternal(context, dso.getType(), dso.getID());
        if (row == null)
        {
            if (dso.getType() == Constants.SITE)
            {
                return Site.getSiteHandle();
            }
            else
            {
                return null;
            }
        }
        else
        {
            return row.getStringColumn("handle");
        }
    }

    /**
     * Return all the handles which start with prefix.
     * 
     * @param context
     *            DSpace context
     * @param prefix
     *            The handle prefix
     * @return A list of the handles starting with prefix. The list is
     *         guaranteed to be non-null. Each element of the list is a String.
     * @exception SQLException
     *                If a database error occurs
     */
    static List<String> getHandlesForPrefix(Context context, String prefix)
            throws SQLException
    {
        String sql = "SELECT handle FROM handle WHERE handle LIKE ? ";
        TableRowIterator iterator = DatabaseManager.queryTable(context, null, sql, prefix+"%");
        List<String> results = new ArrayList<String>();

        try
        {
            while (iterator.hasNext())
            {
                TableRow row = (TableRow) iterator.next();
                results.add(row.getStringColumn("handle"));
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (iterator != null)
            {
                iterator.close();
            }
        }

        return results;
    }

    /**
     * Get the configured Handle prefix string, or a default
     * @return configured prefix or "123456789"
     */
    public static String getPrefix()
    {
        String prefix = ConfigurationManager.getProperty("handle.prefix");
        if (null == prefix)
        {
            prefix = EXAMPLE_PREFIX; // XXX no good way to exit cleanly
            log.error("handle.prefix is not configured; using " + prefix);
        }
        return prefix;
    }

    ////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////

    /**
     * Return the handle for an Object, or null if the Object has no handle.
     * 
     * @param context
     *            DSpace context
     * @param type
     *            The type of object
     * @param id
     *            The id of object
     * @return The handle for object, or null if the object has no handle.
     * @exception SQLException
     *                If a database error occurs
     */
    private static TableRow getHandleInternal(Context context, int type, int id)
            throws SQLException
    {   	
      	String sql = "SELECT * FROM Handle WHERE resource_type_id = ? " +
      				 "AND resource_id = ?";

	return DatabaseManager.querySingleTable(context, "Handle", sql, type, id);
    }

    /**
     * Find the database row corresponding to handle.
     * 
     * @param context
     *            DSpace context
     * @param handle
     *            The handle to resolve
     * @return The database row corresponding to the handle
     * @exception SQLException
     *                If a database error occurs
     */
    private static TableRow findHandleInternal(Context context, String handle)
            throws SQLException
    {
        if (handle == null)
        {
            throw new IllegalArgumentException("Handle is null");
        }

        return DatabaseManager
                .findByUnique(context, "Handle", "handle", handle);
    }

    /**
     * Create a new handle id. The implementation uses the PK of the RDBMS
     * Handle table.
     *
     * @return A new handle id
     * @exception SQLException
     *                If a database error occurs
     */
    public static String createId(int id) throws SQLException {
		String handlePrefix = ConfigurationManager.getProperty("handle.prefix");

		String fullHandle = new StringBuffer().append(handlePrefix).append(
				handlePrefix.endsWith("/") ? "" : "/").append("dryad.").append(
				id).toString();

		log.info("Creating new handle " + fullHandle);
		return fullHandle;
	}


    public static DSpaceObject resolveUrlToDSpaceObject(Context c, String url)
			throws SQLException {
		// We can do nothing with this, return null
		if (url.indexOf("/") == -1) return null;

		String[] splitUrl = url.split("/");

		return HandleManager.resolveToObject(c, splitUrl[splitUrl.length - 2]
				+ "/" + splitUrl[splitUrl.length - 1]);
	}


/**
	 * Pass in a handle of a data package or a data file and we return the
	 * DSpace Item for the data package; otherwise, returns null.  This is
	 * specific to Dryad's concept of data packages and data files.
	 *
	 * @param aContext Current DSpace context
	 * @param aHandle A DSpace handle in the form <code>10255/dryad.230</code>
	 * @return The DSpaceObject for the data package
	 * @throws SQLException If there is a problem interacting with the database
	 */
	public static DSpaceObject resolveToDataPackage(Context aContext,String aHandle) throws SQLException {
		Item item = (Item) HandleManager.resolveToObject(aContext, aHandle);
		String prefix = ConfigurationManager.getProperty("handle.prefix");
		String resolver = ConfigurationManager.getProperty("handle.canonical.prefix");

		if (resolver.endsWith("/")) {
			resolver += (prefix + "/");
		}
		else {
			resolver += ("/" + prefix + "/");
		}

		// If we have a dc.relation.haspart we are a data package; so return
		if (!(item.getMetadata("dc.relation.haspart").length > 0)) {
			DCValue[] dcValues = item.getMetadata("dc.relation.ispartof");

			// Otherwise, check to see if we have a data package relationship
			if (dcValues.length > 0) {
				String handle = dcValues[0].value;
				int start;

				if (handle.startsWith(resolver)) {
					if (log.isDebugEnabled()) {
						log.debug("Processing canonical handle: " + handle);
					}

					handle = prefix + handle.substring(resolver.length() - 1);
				}
				else if ((start = handle.indexOf("/handle/")) != -1) {
					if (log.isDebugEnabled()) {
						log.debug("Processing local handle: " + handle);
					}

					handle = handle.substring(start + 8); // length of /handle/
				}
                // Atmire: added july 2011 resolve the object based on the DOI
                else if (handle.startsWith("doi")) {
                    IdentifierService identifierService = new DSpace().getSingletonService(IdentifierService.class);


                    DSpaceObject dso = null;
                    try {
                        dso = identifierService.resolve(aContext, handle);
                    } catch (IdentifierNotFoundException e) {
                        throw new RuntimeException(e);
                    } catch (IdentifierNotResolvableException e) {
                       throw new RuntimeException(e);
                    }


                    if(dso instanceof Item)
                        handle=dso.getHandle();
                }

				item = (Item) HandleManager.resolveToObject(aContext, handle);
			}
			else {
				if (log.isDebugEnabled()) {
					log.debug("Didn't find a data package for " + aHandle);
				}

				item = null;
			}
		}

		return item;
	}


}
