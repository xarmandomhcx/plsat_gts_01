// ----------------------------------------------------------------------------
// Copyright Zona-OpenGTS
// All rights reserved
// ----------------------------------------------------------------------------
//
// This source module is PROPRIETARY and CONFIDENTIAL.
// NOT INTENDED FOR PUBLIC RELEASE.
// 
// Use of this software is subject to the terms and conditions outlined in
// the 'Commercial' license provided with this software.  If you did not obtain
// a copy of the license with this software please request a copy from the
// Software Provider.
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// ----------------------------------------------------------------------------
// Description:
//  Server Initialization
// ----------------------------------------------------------------------------
// Change History:
//  2012/08/05  Carlos Jesus Gonzalez Ramos
//     -Initial release
//  2012/08/18  Carlos Jesus Gonzalez Ramos
//     -This module is to support devices: Enfora Mini MT / Enfora GSM2338 / Enfora GSM2428 / Enfora Spider MTGu / Skypatrol TT8750
// ----------------------------------------------------------------------------
package org.opengts.servers.suntechst330v148;

import java.lang.*;
import java.util.*;
import java.io.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.dbtypes.*;

import org.opengts.db.*;
import org.opengts.db.tables.*;

/**
*** <code>Main</code> - The main entry point for this device communication server (DCS)
*** module.
**/

public class Main
{
    
    // ------------------------------------------------------------------------

    /* command-line argument keys */
  //public  static final String ARG_DEVCODE[]   = new String[] { "devcode", "dcs" , "serverid" };
    public  static final String ARG_PARSEFILE[] = new String[] { "parse"  , "parseFile" };
    public  static final String ARG_HELP[]      = new String[] { "help"   , "h"   };
  //public  static final String ARG_TCP_PORT[]  = new String[] { "tcp"    , "p"   , "port" };
  //public  static final String ARG_UDP_PORT[]  = new String[] { "udp"    , "p"   , "port" };
    public  static final String ARG_CMD_PORT[]  = new String[] { "command", "cmd" };
    public  static final String ARG_START[]     = new String[] { "start"  };
    public  static final String ARG_DEBUG[]     = new String[] { "debug"  };
    public  static final String ARG_FORMAT[]    = new String[] { "format" , "parseFormat" };
    public  static final String ARG_INSERT[]    = new String[] { "insert" };

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* return server config */
    public static String getServerName()
    {
        return Constants.DEVICE_CODE;
    }

    /* return server config */
    public static DCServerConfig getServerConfig(Device dev)
    {
        return DCServerFactory.getServerConfig(Main.getServerName());
    }

    // ------------------------------------------------------------------------

    /* get server TCP ports (first check command-line, then config file) */
    public static int[] getTcpPorts()
    {
        DCServerConfig dcs = Main.getServerConfig(null);
        if (dcs != null) {
            return dcs.getTcpPorts();
        } else {
            Print.logError("DCServerConfig not found for server: " + getServerName());
            return null;
        }
    }

    /* get server UDP ports (first check command-line, then config file) */
    public static int[] getUdpPorts()
    {
        DCServerConfig dcs = Main.getServerConfig(null);
        if (dcs != null) {
            return dcs.getUdpPorts();
        } else {
            Print.logError("DCServerConfig not found for server: " + getServerName());
            return null;
        }
    }

    /* get server ports (first check command-line, then config file, then default) */
    public static int getCommandDispatcherPort()
    {
        DCServerConfig dcs = Main.getServerConfig(null);
        if (dcs != null) {
            return dcs.getCommandDispatcherPort();
        } else {
            return RTConfig.getInt(ARG_CMD_PORT,0);
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Main entry point

    /* display usage and exit */
    private static void usage(String msg)
    {
        String tcp = StringTools.join(getTcpPorts(),",");
        String udp = StringTools.join(getUdpPorts(),",");
        String cmd = String.valueOf(getCommandDispatcherPort());

        /* print message */
        if (msg != null) {
            Print.logInfo(msg);
        }

        /* print usage */
        String className = Main.class.getName();
        Print.logInfo("");
        Print.logInfo("Usage:");
        Print.logInfo("  java ... " + className + " -h[elp]");
        Print.logInfo(" or");
        Print.logInfo("  java ... " + className + " -parseFile=<filePath>");
        Print.logInfo(" or");
        Print.logInfo("  java ... " + className + " [-port=<port>[,<port>]] -start");
        Print.logInfo("Options:");
        Print.logInfo("  -help               This help");
        Print.logInfo("  [-port=<p>[,<p>]]   Server TCP/UDP port(s) to listen");
        Print.logInfo("  [-tcp=<p>[,<p>]]    Server TCP port(s) to listen on [dft="+tcp+"]");
        Print.logInfo("  [-udp=<p>[,<p>]]    Server UDP port(s) to listen on [dft="+udp+"]");
        Print.logInfo("  [-command=<p>]      Command port to listen on [dft="+cmd+"]");
        Print.logInfo("  [-dcs=<serverId>]   DCServer ID [dft="+Constants.DEVICE_CODE+"]");
        Print.logInfo("  [-format=<parser#>] Parser Format #");
        Print.logInfo("  -start              Start server on the specified port.");
        Print.logInfo("  -parseFile=<file>   File from which data will be parsed.");
        Print.logInfo("");

        /* exit */
        System.exit(1);

    }

    /* main entry point */
    public static void main(String argv[])
    {

        /* configure server for MySQL data store */
        DBConfig.cmdLineInit(argv,false);  // main
        
        /* device code */
        /* obsolete
        DEVICE_CODE = RTConfig.getString(ARG_DEVCODE, Constants.DEVICE_CODE);
        if (StringTools.isBlank(DEVICE_CODE)) {
            Print.logFatal("Invalid device-code specified");
            Main.usage("");
            System.exit(1);
        }
        */

        /* init configuration constants */
        TrackClientPacketHandler.configInit();
        TrackServer.configInit();

        /* header */
        String SEP = "--------------------------------------------------------------------------";
        Print.logInfo(SEP);
        Print.logInfo(Constants.TITLE_NAME + " Server Version " + Constants.VERSION);
        Print.logInfo("DeviceCode           : " + Constants.DEVICE_CODE);        
        Print.logInfo("MinimumSpeed         : " + TrackClientPacketHandler.MINIMUM_SPEED_KPH);
        Print.logInfo("EstimateOdom         : " + TrackClientPacketHandler.ESTIMATE_ODOMETER);
        Print.logInfo("TCP Idle Timeout     : " + TrackServer.getTcpIdleTimeout()    + " ms");
        Print.logInfo("TCP Packet Timeout   : " + TrackServer.getTcpPacketTimeout()  + " ms");
        Print.logInfo("TCP Session Timeout  : " + TrackServer.getTcpSessionTimeout() + " ms");
        Print.logInfo("UDP Idle Timeout     : " + TrackServer.getUdpIdleTimeout()    + " ms");
        Print.logInfo("UDP Packet Timeout   : " + TrackServer.getUdpPacketTimeout()  + " ms");
        Print.logInfo("UDP Session Timeout  : " + TrackServer.getUdpSessionTimeout() + " ms");
        Print.logInfo(Constants.COPYRIGHT);
        Print.logInfo(SEP);

        /* explicit help? */
        if (RTConfig.getBoolean(ARG_HELP,false)) {
            Main.usage("Help ...");
            // control doesn't reach here
            System.exit(0);
        }

        /* make sure the DB is properly initialized */
        if (!DBAdmin.verifyTablesExist()) {
            Print.logFatal("MySQL database has not yet been properly initialized");
            System.exit(1);
        }

        /* 'parseFile'? */
        if (RTConfig.hasProperty(ARG_PARSEFILE)) {
            Print.sysPrintln("Attempting to parse data from file: " + RTConfig.getString(ARG_PARSEFILE));
            RTConfig.setString("parseFile", RTConfig.getString(ARG_PARSEFILE));
            int exit = TrackClientPacketHandler._main(true);
            System.exit(exit);
        }

        /* start server */
        if (RTConfig.getBoolean(ARG_START,false)) {
            
            /* start port listeners */
            try {
                int tcpPorts[]  = getTcpPorts();
                int udpPorts[]  = getUdpPorts();
                int commandPort = getCommandDispatcherPort();
                TrackServer.startTrackServer(tcpPorts, udpPorts, commandPort);
            } catch (Throwable t) { // trap any server exception
                Print.logError("Error: " + t);
            }
            
            /* wait here forever while the server is running in a thread */
            while (true) { 
                try { Thread.sleep(60L * 60L * 1000L); } catch (Throwable t) {} 
            }
            // control never reaches here
            
        }

        /* display usage */
        Main.usage("Missing '-start' ...");
        // control doesn't reach here
        System.exit(1);

    }

}
