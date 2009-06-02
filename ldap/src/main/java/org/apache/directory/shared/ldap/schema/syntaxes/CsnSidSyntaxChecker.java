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
 *  KIND, eCopyOfUuidSyntaxCheckerither express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.shared.ldap.schema.syntaxes;

import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.schema.AbstractSyntaxChecker;
import org.apache.directory.shared.ldap.schema.SyntaxChecker;


/**
 * An CSN SID syntax checker.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 736240 $
 */
public class CsnSidSyntaxChecker extends AbstractSyntaxChecker
{
    /** the Apache assigned internal OID for this syntax checker */
    public static final SyntaxChecker INSTANCE = new CsnSidSyntaxChecker();


    /**
     * Creates a new instance of CsnSyntaxChecker.
     */
    public CsnSidSyntaxChecker()
    {
        super( SchemaConstants.CSN_SID_SYNTAX );
    }


    /**
     * 
     * Creates a new instance of CsnSidSyntaxChecker.
     * 
     * @param oid the oid to associate with this new SyntaxChecker
     *
     */
    protected CsnSidSyntaxChecker( String oid )
    {
        super( oid );
    }

    
    /**
     * @see org.apache.directory.shared.ldap.schema.SyntaxChecker#isValidSyntax(Object)
     * 
     * The value is stored as a String internally.
     * 
     * @param value the value of some attribute with the syntax
     * @return true if the value is in the valid syntax, false otherwise
     */
    public boolean isValidSyntax( Object value )
    {
        if ( value == null )
        {
            return false;
        }
        
        if ( ! ( value instanceof String ) )
        {
            return false;
        }
        
        String sidStr = (String)value;
        
        if ( sidStr.length() > 3 )
        {
            return false;
        }

        // The SID must be an hexadecimal number between 0x00 and 0xFFF
        
        try
        {
            int sid = Integer.parseInt( sidStr, 16 );
            
            if ( ( sid < 0 ) || ( sid > 0x0fff ) )
            {
                return false;
            }
        }
        catch ( NumberFormatException nfe )
        {
            return false;
        }
        
        return true;
    }
}
