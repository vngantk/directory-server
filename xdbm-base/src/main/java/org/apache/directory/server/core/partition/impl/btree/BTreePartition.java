/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.core.partition.impl.btree;


import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.naming.directory.SearchControls;

import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.BaseEntryFilteringCursor;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.EntryOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.AbstractPartition;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.xdbm.search.Optimizer;
import org.apache.directory.server.xdbm.search.SearchEngine;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.exception.LdapContextNotEmptyException;
import org.apache.directory.shared.ldap.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchObjectException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * An abstract {@link Partition} that uses general BTree operations.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public abstract class BTreePartition<ID> extends AbstractPartition
{

    /** the search engine used to search the database */
    protected SearchEngine<ServerEntry, ID> searchEngine;
    protected Optimizer optimizer;

    protected SchemaManager schemaManager;

    protected String id;
    protected int cacheSize = -1;
    protected DN suffixDn;
    private File partitionDir;

    /** The rootDSE context */
    protected ServerEntry contextEntry;
    private Set<Index<? extends Object, ServerEntry, ID>> indexedAttributes;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates a B-tree based context partition.
     */
    protected BTreePartition()
    {
        indexedAttributes = new HashSet<Index<? extends Object, ServerEntry, ID>>();
    }


    // ------------------------------------------------------------------------
    // C O N F I G U R A T I O N   M E T H O D S
    // ------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public void setSchemaManager( SchemaManager schemaManager )
    {
        this.schemaManager = schemaManager;
    }


    /**
     * {@inheritDoc}
     */
    public SchemaManager getSchemaManager()
    {
        return schemaManager;
    }


    /**
     * Gets the directory in which this Partition stores files.
     *
     * @return the directory in which this Partition stores files.
     */
    public File getPartitionDir()
    {
        return partitionDir;
    }


    /**
     * Sets the directory in which this Partition stores files.
     *
     * @param partitionDir the directory in which this Partition stores files.
     */
    public void setPartitionDir( File partitionDir )
    {
        this.partitionDir = partitionDir;
    }


    public void setIndexedAttributes( Set<Index<? extends Object, ServerEntry, ID>> indexedAttributes )
    {
        this.indexedAttributes = indexedAttributes;
    }


    public void addIndexedAttributes( Index<? extends Object, ServerEntry, ID>... indexes )
    {
        for ( Index<? extends Object, ServerEntry, ID> index : indexes )
        {
            indexedAttributes.add( index );
        }
    }


    public Set<Index<? extends Object, ServerEntry, ID>> getIndexedAttributes()
    {
        return indexedAttributes;
    }


    /**
     * Used to specify the entry cache size for a Partition.  Various Partition
     * implementations may interpret this value in different ways: i.e. total cache
     * size limit verses the number of entries to cache.
     *
     * @param cacheSize the maximum size of the cache in the number of entries
     */
    public void setCacheSize( int cacheSize )
    {
        this.cacheSize = cacheSize;
    }


    /**
     * Gets the entry cache size for this BTreePartition.
     *
     * @return the maximum size of the cache as the number of entries maximum before paging out
     */
    public int getCacheSize()
    {
        return cacheSize;
    }


    /**
     * Gets the unique identifier for this partition.
     *
     * @return the unique identifier for this partition
     */
    public String getId()
    {
        return id;
    }


    /**
     * Sets the unique identifier for this partition.
     *
     * @param id the unique identifier for this partition
     */
    public void setId( String id )
    {
        this.id = id;
    }


    // -----------------------------------------------------------------------
    // E N D   C O N F I G U R A T I O N   M E T H O D S
    // -----------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // Public Accessors - not declared in any interfaces just for this class
    // ------------------------------------------------------------------------

    /**
     * Gets the DefaultSearchEngine used by this ContextPartition to search the
     * Database. 
     *
     * @return the search engine
     */
    public SearchEngine<ServerEntry, ID> getSearchEngine()
    {
        return searchEngine;
    }


    // ------------------------------------------------------------------------
    // Partition Interface Method Implementations
    // ------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public void delete( DeleteOperationContext opContext ) throws Exception
    {
        DN dn = opContext.getDn();

        ID id = getEntryId( dn );

        // don't continue if id is null
        if ( id == null )
        {
            throw new LdapNoSuchObjectException( I18n.err( I18n.ERR_699, dn ) );
        }

        if ( getChildCount( id ) > 0 )
        {
            LdapContextNotEmptyException cnee = new LdapContextNotEmptyException( I18n.err( I18n.ERR_700, dn ) );
            //cnee.setRemainingName( dn );
            throw cnee;
        }

        delete( id );
    }


    public abstract void add( AddOperationContext opContext ) throws Exception;


    public abstract void modify( ModifyOperationContext opContext ) throws Exception;


    public EntryFilteringCursor list( ListOperationContext opContext ) throws Exception
    {
        return new BaseEntryFilteringCursor( new ServerEntryCursorAdaptor<ID>( this, list( getEntryId( opContext
            .getDn() ) ) ), opContext );
    }


    public EntryFilteringCursor search( SearchOperationContext opContext ) throws Exception
    {
        SearchControls searchCtls = opContext.getSearchControls();
        IndexCursor<ID, ServerEntry, ID> underlying;

        underlying = searchEngine.cursor( opContext.getDn(), opContext.getAliasDerefMode(), opContext.getFilter(),
            searchCtls );

        return new BaseEntryFilteringCursor( new ServerEntryCursorAdaptor<ID>( this, underlying ), opContext );
    }


    public ClonedServerEntry lookup( LookupOperationContext opContext ) throws Exception
    {
        ID id = getEntryId( opContext.getDn() );

        if ( id == null )
        {
            return null;
        }

        ClonedServerEntry entry = lookup( id );

        if ( ( opContext.getAttrsId() == null ) || ( opContext.getAttrsId().size() == 0 ) )
        {
            return entry;
        }

        for ( AttributeType attributeType : ( ( ServerEntry ) entry.getOriginalEntry() ).getAttributeTypes() )
        {
            if ( !opContext.getAttrsId().contains( attributeType.getOid() ) )
            {
                entry.removeAttributes( attributeType );
            }
        }

        return entry;
    }


    public boolean hasEntry( EntryOperationContext opContext ) throws Exception
    {
        return null != getEntryId( opContext.getDn() );
    }


    public abstract void rename( RenameOperationContext opContext ) throws Exception;


    public abstract void move( MoveOperationContext opContext ) throws Exception;


    public abstract void moveAndRename( MoveAndRenameOperationContext opContext ) throws Exception;


    public abstract void sync() throws Exception;


    ////////////////////
    // public abstract methods

    // ------------------------------------------------------------------------
    // Index Operations 
    // ------------------------------------------------------------------------

    public abstract void addIndexOn( Index<? extends Object, ServerEntry, ID> index ) throws Exception;


    public abstract boolean hasUserIndexOn( String attribute ) throws Exception;


    public abstract boolean hasSystemIndexOn( String attribute ) throws Exception;


    public abstract Index<String, ServerEntry, ID> getPresenceIndex();


    /**
     * Gets the Index mapping the primary keys of parents to the 
     * primary keys of their children.
     *
     * @return the one level Index
     */
    public abstract Index<ID, ServerEntry, ID> getOneLevelIndex();


    /**
     * Gets the Index mapping the primary keys of ancestors to the 
     * primary keys of their descendants.
     *
     * @return the sub tree level Index
     */
    public abstract Index<ID, ServerEntry, ID> getSubLevelIndex();


    /**
     * Gets the alias index mapping parent entries with scope expanding aliases 
     * children one level below them; this system index is used to dereference
     * aliases on one/single level scoped searches.
     * 
     * @return the one alias index
     */
    public abstract Index<ID, ServerEntry, ID> getOneAliasIndex();


    /**
     * Gets the alias index mapping relative entries with scope expanding 
     * alias descendents; this system index is used to dereference aliases on 
     * subtree scoped searches.
     * 
     * @return the sub alias index
     */
    public abstract Index<ID, ServerEntry, ID> getSubAliasIndex();


    /**
     * Gets the system index defined on the ALIAS_ATTRIBUTE which for LDAP would
     * be the aliasedObjectName and for X.500 would be aliasedEntryName.
     * 
     * @return the index on the ALIAS_ATTRIBUTE
     */
    public abstract Index<String, ServerEntry, ID> getAliasIndex();


    /**
     * {@inheritDoc}
     */
    public void setSuffix( String suffix ) throws LdapInvalidDnException
    {
        this.suffixDn = new DN( suffix );
    }


    /**
     * {@inheritDoc}
     */
    public String getSuffix()
    {
        return suffixDn.getName();
    }


    /**
     * {@inheritDoc}
     */
    public DN getSuffixDn()
    {
        return suffixDn;
    }


    public abstract Index<? extends Object, ServerEntry, ID> getUserIndex( String attribute ) throws Exception;


    public abstract Index<? extends Object, ServerEntry, ID> getSystemIndex( String attribute ) throws Exception;


    public abstract ID getEntryId( DN dn ) throws Exception;


    public abstract String getEntryDn( ID id ) throws Exception;


    public abstract ID getParentId( String dn ) throws Exception;


    public abstract ID getParentId( ID childId ) throws Exception;


    /**
     * Gets the user provided distinguished name.
     *
     * @param id the entry id
     * @return the user provided distinguished name
     * @throws Exception if the updn index cannot be accessed
     */
    public abstract String getEntryUpdn( ID id ) throws Exception;


    /**
     * Gets the user provided distinguished name.
     *
     * @param dn the normalized distinguished name
     * @return the user provided distinguished name
     * @throws Exception if the updn and ndn indices cannot be accessed
     */
    public abstract String getEntryUpdn( String dn ) throws Exception;


    public abstract ClonedServerEntry lookup( ID id ) throws Exception;


    public abstract void delete( ID id ) throws Exception;


    public abstract IndexCursor<ID, ServerEntry, ID> list( ID id ) throws Exception;


    public abstract int getChildCount( ID id ) throws Exception;


    public abstract void setProperty( String key, String value ) throws Exception;


    public abstract String getProperty( String key ) throws Exception;


    public abstract Iterator<String> getUserIndices();


    public abstract Iterator<String> getSystemIndices();


    /**
     * Gets the count of the total number of entries in the database.
     *
     * TODO shouldn't this be a BigInteger instead of an int? 
     * 
     * @return the number of entries in the database 
     * @throws Exception if there is a failure to read the count
     */
    public abstract int count() throws Exception;
}
