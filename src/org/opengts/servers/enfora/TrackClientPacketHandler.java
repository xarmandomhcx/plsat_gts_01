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
package org.opengts.servers.enfora;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.sql.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.dbtypes.*;
import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.servers.*;

public class TrackClientPacketHandler
    extends AbstractClientPacketHandler
{

    // ------------------------------------------------------------------------   
    // ------------------------------------------------------------------------
    
	public  static       String  UNIQUEID_PREFIX[]          = null;
    public  static       boolean ESTIMATE_ODOMETER          = true;  
    public  static       boolean SIMEVENT_GEOZONES          = true;       
    public  static       long    SIMEVENT_DIGITAL_INPUTS    = 0x0000L; // 0xFFFFL;
	public  static       double  MINIMUM_SPEED_KPH          = 0.0;
	public  static       double  MINIMUM_MOVED_METERS       = 0.0;
	public  static final double  KILOMETERS_PER_KNOT        = 1.85200000;
    public  static final double  KNOTS_PER_KILOMETER        = 1.0 / KILOMETERS_PER_KNOT;

    /* flag indicating whether data should be inserted into the DB */
    // should be set to 'true' for production.
    private static       boolean DFT_INSERT_EVENT           = true;
    private static       boolean INSERT_EVENT               = DFT_INSERT_EVENT;

    /* update Device record */
    // (enable to update Device record with current IP address and last connect time)
    private static       boolean UPDATE_DEVICE              = false;    

    // ------------------------------------------------------------------------
    private static       boolean IGNORE_NMEA_CHECKSUM       = false;
    // ------------------------------------------------------------------------
    /* GMT/UTC timezone */
    private static final TimeZone gmtTimezone               = DateTime.getGMTTimeZone();
    // ------------------------------------------------------------------------
	/* count the number of events we've parsed during this session */
    private int             eventTotalCount                 = 0;

    /* GTS status codes for Input-On events */
    private static final int InputStatusCodes_ON[] = new int[] {
        StatusCodes.STATUS_INPUT_ON_00,
        StatusCodes.STATUS_INPUT_ON_01,
        StatusCodes.STATUS_INPUT_ON_02,
        StatusCodes.STATUS_INPUT_ON_03,
        StatusCodes.STATUS_INPUT_ON_04,
        StatusCodes.STATUS_INPUT_ON_05,
        StatusCodes.STATUS_INPUT_ON_06,
        StatusCodes.STATUS_INPUT_ON_07,
        StatusCodes.STATUS_INPUT_ON_08,
        StatusCodes.STATUS_INPUT_ON_09,
        StatusCodes.STATUS_INPUT_ON_10,
        StatusCodes.STATUS_INPUT_ON_11,
        StatusCodes.STATUS_INPUT_ON_12,
        StatusCodes.STATUS_INPUT_ON_13,
        StatusCodes.STATUS_INPUT_ON_14,
        StatusCodes.STATUS_INPUT_ON_15
    };

    /* GTS status codes for Input-Off events */
    private static final int InputStatusCodes_OFF[] = new int[] {
        StatusCodes.STATUS_INPUT_OFF_00,
        StatusCodes.STATUS_INPUT_OFF_01,
        StatusCodes.STATUS_INPUT_OFF_02,
        StatusCodes.STATUS_INPUT_OFF_03,
        StatusCodes.STATUS_INPUT_OFF_04,
        StatusCodes.STATUS_INPUT_OFF_05,
        StatusCodes.STATUS_INPUT_OFF_06,
        StatusCodes.STATUS_INPUT_OFF_07,
        StatusCodes.STATUS_INPUT_OFF_08,
        StatusCodes.STATUS_INPUT_OFF_09,
        StatusCodes.STATUS_INPUT_OFF_10,
        StatusCodes.STATUS_INPUT_OFF_11,
        StatusCodes.STATUS_INPUT_OFF_12,
        StatusCodes.STATUS_INPUT_OFF_13,
        StatusCodes.STATUS_INPUT_OFF_14,
        StatusCodes.STATUS_INPUT_OFF_15
    };

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* TCP session ID */
    private String          sessionID                   = null;
    
    /* common GPSEvent instance */
    private GPSEvent        gpsEvent                    = null;
    
    /* Session 'terminate' indicator */    
    private boolean         terminate                   = false;

    /* session IP address */    
    private String          ipAddress                   = null;
    private int             clientPort                  = 0;

    /* packet handler constructor */
    public TrackClientPacketHandler() 
    {
        super();        
    }

    // ------------------------------------------------------------------------

    /* callback when session is starting */
    // this method is called at the beginning of a communication session
    public void sessionStarted(InetAddress inetAddr, boolean isTCP, boolean isText)
    {
        super.sessionStarted(inetAddr, isTCP, isText);
        super.clearTerminateSession();

        /* init */
        this.ipAddress        = (inetAddr != null)? inetAddr.getHostAddress() : null;
        this.clientPort       = this.getSessionInfo().getRemotePort();

    }

    /* callback when session is terminating */
    // this method is called at the end of a communication session
    public void sessionTerminated(Throwable err, long readCount, long writeCount)
    {
        super.sessionTerminated(err, readCount, writeCount);
    }

    // ------------------------------------------------------------------------

    /* callback to return the TCP session id */
    public String getSessionID()
    {
        if (!StringTools.isBlank(this.sessionID)) {
            return this.sessionID;
        } else
        if (this.gpsEvent != null) {
            return DCServerFactory.createTcpSessionID(this.gpsEvent.getDevice());
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------

    /* based on the supplied packet data, return the remaining bytes to read in the packet */
    public int getActualPacketLength(byte packet[], int packetLen)
    {        
        if (Constants.ASCII_PACKETS) {         
            return ServerSocketThread.PACKET_LEN_LINE_TERMINATOR;          
        } else {            
            return ServerSocketThread.PACKET_LEN_LINE_TERMINATOR; 
        }        
    }

    // ------------------------------------------------------------------------

    /* set session terminate after next packet handling */
    private void setTerminate()
    {
        this.terminate = true;
    }
    
    /* indicate that the session should terminate */
    public boolean terminateSession()
    {
        return this.terminate;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* return the initial packet sent to the device after session is open */
    public byte[] getInitialPacket() 
        throws Exception
    {       
        return null;        
    }

    /* workhorse of the packet handler */
    public byte[] getHandlePacket(byte pktBytes[]) 
    {
	
	String hex = StringTools.toHexString(pktBytes);	
	String Ascii = StringTools.toStringValue(pktBytes).trim();
	String s = Ascii.replaceAll(" ", "");
	Print.logInfo("Recv[HEX]: " + hex);
    Print.logInfo("Recv[ASCII]: " + s); // debug message
	
	if (s.contains("$GPRMC")) {    
    return this.parseInsertRecord_Enfora(s);
    }
    return null; // no return packets are expected
    }

    /* final packet sent to device before session is closed */
    public byte[] getFinalPacket(boolean hasError) 
        throws Exception
    {
        return null;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    /* parse and insert data record */
    private byte[] parseInsertRecord_Enfora(String s)
    {   
        // Ascii Data:	
        // Example:
        // -------  
        // 10             SeC001398 D1,DF 12 $GPRMC,214404.00,A,1917.230697,N,09935.738666,W,54.3,91.7,140612,5.0,W,A*31  605
        // 10           = Event identifier 
		// SeC001398    = Device ID - MDMID 
		// D1           = GPIOs        
		// ,            = Separator
		// DF           = Status of GPIOs
		// 12           = Type Event
		// $GPRMC       = Sentence NMEA
		// ,            = Separator
		// 214404.00    = UTC time of position HHMMSS
		// ,            = Separator
		// A            = GPS Valid
		// ,            = Separator
		// 1917.230697  = Latitude in ddmm.mm format
		// ,            = Separator
		// N            = Latitude hemisphere ("N" = northern, "S" = southern)
		// ,            = Separator
		// 09935.738666 = Longitude in dddmm.mm format
		// ,            = Separator
		// W            = Longitude hemisphere ("E" = eastern, "W" = western)
		// ,            = Separator
		// 54.3         = Speed in knots
		// ,            = Separator
		// 91.7         = Heading in degrees
		// ,            = Separator
		// 140612       = Date in DDMMYY format
		// ,            = Separator
		// 5.0          = Magnetic variation in degrees
		// ,            = Separator
		// W            = Direction of magnetic variation ("E" = east, "W" = west)
		// ,            = Separator
		// A*31         = Checksum
		// 605          = Odometer in meters (Only if configured)
        				
		/* pre-validate */
        if (s == null) {
            Print.logError("String is null");
            return null;
        }
        if (s.contains(",,V,,,,,,,,,,")) {
            Print.logError("Cadena no valida para localizacion, se descarta........");
            return null;
        }
        Print.logInfo("Parsing(Enfora): " + s);

        /* parse to fields */
        String fld[] = StringTools.parseString(s, ',');
		
		/* get "imei:" */
        String mobileID = null;       
        String reportType = null; 

        if (fld[0].length() == 12) {
          mobileID = fld[0].substring(1, fld[0].length() - 2);
          reportType = fld[0].substring(0,1);
        } else {
          mobileID = fld[0].substring(2, fld[0].length() - 2);
          reportType = fld[0].substring(0,2);
        }
		
		/* parse */  		
		long    hms         = StringTools.parseLong(fld[ 2], 0L);
        long    dmy         = StringTools.parseLong(fld[10], 0L);
        long    fixtime     = this._getUTCSeconds(dmy, hms);
		boolean validGPS    = fld[3].equalsIgnoreCase("A");	
        double  latitude    = validGPS? this._parseLatitude( fld[4], fld[5])  : 0.0;
        double  longitude   = validGPS? this._parseLongitude(fld[6], fld[7])  : 0.0;		
		double  knots       = validGPS? StringTools.parseDouble(fld[8], -1.0) : 0.0;
        double  headingDeg  = validGPS? StringTools.parseDouble(fld[9], -1.0) : 0.0;
        double  speedKPH    = (knots >= 0.0)? (knots * KILOMETERS_PER_KNOT)   : -1.0;     		
		double  hDOP        = 0.0;
		int     numSats     = 0;
        double  altitudeM   = 0.0;		
		double  odomKM      = 0.0; // fld[13].substring(4, fld[0].length()); /* Odometer Virtual GPS*/
        long    gpioInput   = 0L;

        int     statusCode  = 0;

        if (reportType.equals("10")) {
          statusCode = StatusCodes.STATUS_MOTION_IN_MOTION;
        } else if (reportType.equals("11")) {
          statusCode = StatusCodes.STATUS_LOCATION;
        } else if (reportType.equals("20")) {
          statusCode = StatusCodes.STATUS_IGNITION_OFF;
        } else if (reportType.equals("22")) {
          statusCode = StatusCodes.STATUS_IGNITION_OFF;
        } else if (reportType.equals("21")) {
          statusCode = StatusCodes.STATUS_IGNITION_ON;
        } else if (reportType.equals("97")) {
          statusCode = StatusCodes.STATUS_RESUME;
        } else if (reportType.equals("98")) {
          statusCode = StatusCodes.STATUS_SUSPEND;
        } else if (reportType.equals("72")) {
          statusCode = StatusCodes.STATUS_PANIC_ON;
        } else if (reportType.equals("73")) {
          statusCode = StatusCodes.STATUS_PANIC_ON;
        } else if (reportType.equals("9")) {
          statusCode = StatusCodes.STATUS_POWER_FAILURE;
        } else {
          statusCode = StatusCodes.STATUS_NOTIFY;
        } 
/*agregar arriba bloqueo STATUS_SUSPEND y desbloqueo STATUS_RESUME, solo falta saber que es 97 y 98*/

        long justNow = DateTime.getCurrentTimeSec();
        long justTomorrow = justNow + (60 * 60 * 24);
        Print.logInfo("Justo ahora::::::" + justNow + "[" + new DateTime(justNow) + "]");
        Print.logInfo("Justo manana:::::" + justTomorrow + "[" + new DateTime(justTomorrow) + "]");

        /* invalid date? */
        if (fixtime <= 0L) {
            Print.logWarn("Invalid date: ");
            fixtime = DateTime.getCurrentTimeSec(); // default to now
        }
            
        GeoPoint geoPoint = new GeoPoint(latitude,longitude);

        /* adjustments to received values */
        if (speedKPH < MINIMUM_SPEED_KPH) {
            speedKPH   = 0.0;
            headingDeg = 0.0;
        } else
        if (headingDeg < 0.0) { 
            headingDeg = 0.0;
        }
        
        /* debug */		
        Print.logInfo("MobileID  : " + mobileID);		
        Print.logInfo("Timestamp: "  + fixtime + " [" + new DateTime(fixtime) + "]");		
        if (fixtime >= justTomorrow) {
          Print.logInfo("fecha futura >>>>>>>>>>>>>>>>>>>");
          return null;
        }
/////        Print.logInfo("GeoPoint  : " + geoPoint);
/////        Print.logInfo("Speed    : "  + StringTools.format(speedKPH,"#0.0") + " kph " + headingDeg);

        /* mobile-id */
        if (StringTools.isBlank(mobileID)) {
            Print.logError("Missing MobileID");
            return null;
        }

        /* find Device */
        String accountID = "";
        String deviceID  = "";
        String uniqueID  = "";
        Device device = DCServerFactory.loadDeviceByPrefixedModemID(UNIQUEID_PREFIX, mobileID);
        if (device == null) {
            return null; // errors already displayed
        } else {
            accountID = device.getAccountID();
            deviceID  = device.getDeviceID();
            uniqueID  = device.getUniqueID();
            Print.logInfo("UniqueID  : " + uniqueID);
            Print.logInfo("DeviceID  : " + accountID + "/" + deviceID);
        }
        
        /* check IP address */
        DataTransport dataXPort = device.getDataTransport();
        if ((this.ipAddress != null) && !dataXPort.isValidIPAddress(this.ipAddress)) {
            DTIPAddrList validIPAddr = dataXPort.getIpAddressValid(); // may be null
            Print.logError("Invalid IP Address from device: " + this.ipAddress + " [expecting " + validIPAddr + "]");
            return null;
        }
        dataXPort.setIpAddressCurrent(this.ipAddress);    // FLD_ipAddressCurrent
        dataXPort.setRemotePortCurrent(this.clientPort);  // FLD_remotePortCurrent
        dataXPort.setLastTotalConnectTime(DateTime.getCurrentTimeSec()); // FLD_lastTotalConnectTime
        if (!dataXPort.getDeviceCode().equalsIgnoreCase(Constants.DEVICE_CODE)) {
            dataXPort.setDeviceCode(Constants.DEVICE_CODE); // FLD_deviceCode
        }        

        /* estimate GPS-based odometer */
        if (odomKM <= 0.0) {
            // calculate odometer
            odomKM = (ESTIMATE_ODOMETER && validGPS)? 
                device.getNextOdometerKM(geoPoint) : 
                device.getLastOdometerKM();
        } else {
            // bounds-check odometer
            odomKM = device.adjustOdometerKM(odomKM);
        }
        Print.logInfo("OdometerKM: " + odomKM);

        /* simulate Geozone arrival/departure */
        if (SIMEVENT_GEOZONES && validGPS) {
            java.util.List<Device.GeozoneTransition> zone = device.checkGeozoneTransitions(fixtime, geoPoint);
            if (zone != null) {
                for (Device.GeozoneTransition z : zone) {
                    this.insertEventRecord(device, 
                        z.getTimestamp(), z.getStatusCode(), z.getGeozone(),
                        geoPoint, gpioInput, speedKPH, headingDeg, altitudeM, odomKM, hDOP);
                    Print.logInfo("Geozone    : " + z);
                    if (z.getStatusCode() == statusCode) {
                        // suppress 'statusCode' event if we just added it here
                        Print.logDebug("StatusCode already inserted: 0x" + StatusCodes.GetHex(statusCode));
                        statusCode = StatusCodes.STATUS_IGNORE;
                    }
                }
            }
        }

        /* insert event */
        if (statusCode == StatusCodes.STATUS_NONE) {
            // ignore this event
        } else
        if ((statusCode != StatusCodes.STATUS_LOCATION) || !validGPS) {
            this.insertEventRecord(device, 
                fixtime, statusCode, null/*GeoZone*/,
                geoPoint, gpioInput, speedKPH, headingDeg, altitudeM, odomKM, hDOP);
        } else
        if (!device.isNearLastValidLocation(geoPoint,MINIMUM_MOVED_METERS)) {
            if ((statusCode == StatusCodes.STATUS_LOCATION) && (speedKPH > 0.0)) {
                statusCode = StatusCodes.STATUS_MOTION_IN_MOTION;
            }
            this.insertEventRecord(device, 
                fixtime, statusCode, null/*GeoZone*/,
                geoPoint, gpioInput, speedKPH, headingDeg, altitudeM, odomKM, hDOP);
				
        }
            
        /* save device changes */
        try {
            // TODO: check "this.device" vs "this.dataXPort"   
            device.updateChangedEventFields();	
			
        } catch (DBException dbe) {
            Print.logException("Unable to update Device: " + accountID + "/" + deviceID, dbe);
        } finally {
            //
        }	
        
		return null;// //return required acknowledgement (ACK) back to the device
		
    }    
    
	// ------------------------------------------------------------------------
	// ------------------------------------------------------------------------

    /**
    *** Computes seconds in UTC time given values from GPS device.
    *** @param dmy    Date received from GPS in DDMMYY format, where DD is day, MM is month,
    ***               YY is year.
    *** @param hms    Time received from GPS in HHMMSS format, where HH is hour, MM is minute,
    ***               and SS is second.
    *** @return Time in UTC seconds.
    ***/
    private long _getUTCSeconds(long dmy, long hms)
    {
    
        /* time of day [TOD] */
        int    HH  = (int)((hms / 10000L) % 100L);
        int    MM  = (int)((hms / 100L) % 100L);
        int    SS  = (int)(hms % 100L);
        long   TOD = (HH * 3600L) + (MM * 60L) + SS;
    
        /* current UTC day */
        long DAY;
        if (dmy > 0L) {
            int    yy  = (int)(dmy % 100L) + 2000;
            int    mm  = (int)((dmy / 100L) % 100L);
            int    dd  = (int)((dmy / 10000L) % 100L);
            long   yr  = ((long)yy * 1000L) + (long)(((mm - 3) * 1000) / 12);
            DAY        = ((367L * yr + 625L) / 1000L) - (2L * (yr / 1000L))
                         + (yr / 4000L) - (yr / 100000L) + (yr / 400000L)
                         + (long)dd - 719469L;
        } else {
            // we don't have the day, so we need to figure out as close as we can what it should be.
            long   utc = DateTime.getCurrentTimeSec();
            long   tod = utc % DateTime.DaySeconds(1);
            DAY        = utc / DateTime.DaySeconds(1);
            long   dif = (tod >= TOD)? (tod - TOD) : (TOD - tod); // difference should be small (ie. < 1 hour)
            if (dif > DateTime.HourSeconds(12)) { // 12 to 18 hours
                // > 12 hour difference, assume we've crossed a day boundary
                if (tod > TOD) {
                    // tod > TOD likely represents the next day
                    DAY++;
                } else {
                    // tod < TOD likely represents the previous day
                    DAY--;
                }
            }
        }
        
        /* return UTC seconds */
        long sec = DateTime.DaySeconds(DAY) + TOD;
        return sec;
        
    }

    /**
    *** Parses latitude given values from GPS device.
    *** @param  s  Latitude String from GPS device in ddmm.mm format.
    *** @param  d  Latitude hemisphere, "N" for northern, "S" for southern.
    *** @return Latitude parsed from GPS data, with appropriate sign based on hemisphere or
    ***         90.0 if invalid latitude provided.
    **/
    private double _parseLatitude(String s, String d)
    {
        double _lat = StringTools.parseDouble(s, 99999.0);
        if (_lat < 99999.0) {
            double lat = (double)((long)_lat / 100L); // _lat is always positive here
            lat += (_lat - (lat * 100.0)) / 60.0;
            return d.equals("S")? -lat : lat;
        } else {
            return 90.0; // invalid latitude
        }
    }

    /**
    *** Parses longitude given values from GPS device.
    *** @param s Longitude String from GPS device in ddmm.mm format.
    *** @param d Longitude hemisphere, "E" for eastern, "W" for western.
    *** @return Longitude parsed from GPS data, with appropriate sign based on hemisphere or
    *** 180.0 if invalid longitude provided.
    **/
    private double _parseLongitude(String s, String d)
    {
        double _lon = StringTools.parseDouble(s, 99999.0);
        if (_lon < 99999.0) {
            double lon = (double)((long)_lon / 100L); // _lon is always positive here
            lon += (_lon - (lon * 100.0)) / 60.0;
            return d.equals("W")? -lon : lon;
        } else {
            return 180.0; // invalid longitude
        }
    }
	
	// ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
     private EventData createEventRecord(Device device, 
        long     fixtime,
        int      statusCode,
        GeoPoint geoPoint, 
        long     gpioInput,
        double   speedKPH, double heading, 
        double   altitude,
        double   odomKM,
		double   hDOP   )
    {
        String accountID    = (device != null)? device.getAccountID() : "";
        String deviceID     = (device != null)? device.getDeviceID()  : "";
        EventData.Key evKey = new EventData.Key(accountID, deviceID, fixtime, statusCode);
        EventData evdb      = evKey.getDBRecord();
        evdb.setGeoPoint(geoPoint);
        evdb.setInputMask(gpioInput);
        evdb.setHeading(heading);
        evdb.setSpeedKPH(speedKPH);
        evdb.setAltitude(altitude);
        evdb.setOdometerKM(odomKM);
		evdb.setHDOP(hDOP);
        return evdb;
    }

    /* create and insert an event record */
    private void insertEventRecord(Device device, 
        long     fixtime, int statusCode, Geozone geozone,
        GeoPoint geoPoint,                             
        long     gpioInput,
        double   speedKPH, double heading, 
        double   altitude,
        double   odomKM,
		double   hDOP   )
    {

        /* create event */
        EventData evdb = createEventRecord(device, fixtime, statusCode, geoPoint, gpioInput, speedKPH, heading, altitude, odomKM, hDOP);

        /* insert event */
        // this will display an error if it was unable to store the event
        Print.logInfo("Event     : [0x" + StringTools.toHexString(statusCode,16) + "] " + StatusCodes.GetDescription(statusCode,null));
        if (device != null) {
            device.insertEventData(evdb);            
        }
        this.eventTotalCount++;

    }
    // ------------------------------------------------------------------------
	
    /**
    *** Initialize runtime configuration
    **/
    public static void configInit() 
    {
        DCServerConfig dcsc     = Main.getServerConfig(null);
        if (dcsc == null) {
            Print.logWarn("DCServer not found: " + Main.getServerName());
            return;
        }
       
        /* common */
        MINIMUM_SPEED_KPH       = dcsc.getMinimumSpeedKPH(MINIMUM_SPEED_KPH);
        ESTIMATE_ODOMETER       = dcsc.getEstimateOdometer(ESTIMATE_ODOMETER);
        SIMEVENT_GEOZONES       = dcsc.getSimulateGeozones(SIMEVENT_GEOZONES);
        SIMEVENT_DIGITAL_INPUTS = dcsc.getSimulateDigitalInputs(SIMEVENT_DIGITAL_INPUTS) & 0xFFFFL;

    }

    // ------------------------------------------------------------------------
	// ------------------------------------------------------------------------
	
    private static int _usage()
    {
        String cn = StringTools.className(TrackClientPacketHandler.class);
        Print.sysPrintln("Test/Load Device Communication Server");
        Print.sysPrintln("Usage:");
        Print.sysPrintln("  $JAVA_HOME/bin/java -classpath <classpath> %s {options}", cn);
        Print.sysPrintln("Options:");
        Print.sysPrintln("  -insert=[true|false]    Insert parsed records into EventData");        
        Print.sysPrintln("  -parseFile=<file>       Parse data from specified file");
        return 1;
    }
	// ------------------------------------------------------------------------
	// ------------------------------------------------------------------------
	
	 /**
    *** Main entry point used for debug purposes only
    **/
    public static int _main(boolean fromMain)
    {

        /* default options */
        INSERT_EVENT = RTConfig.getBoolean(Main.ARG_INSERT, DFT_INSERT_EVENT);
        if (!INSERT_EVENT) {
            Print.sysPrintln("Warning: Data will NOT be inserted into the database");
        }

        /* create client packet handler */
        TrackClientPacketHandler tcph = new TrackClientPacketHandler();  

        /* 'parseFile' specified? */
        if (RTConfig.hasProperty(Main.ARG_PARSEFILE)) {

            /* get input file */
            File parseFile = RTConfig.getFile(Main.ARG_PARSEFILE,null);
            if ((parseFile == null) || !parseFile.isFile()) {
                Print.sysPrintln("Data source file not specified, or does not exist.");
                return _usage();
            }

            /* open file */
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(parseFile);
            } catch (IOException ioe) {
                Print.logException("Error openning input file: " + parseFile, ioe);
                return 2;
            }

            /* loop through file */
            try {
                // records are assumed to be terminated by CR/NL 
                for (;;) {
                    String data = FileTools.readLine(fis);
                    if (!StringTools.isBlank(data)) {
                        tcph.getHandlePacket(data.getBytes());
                    }
                }
            } catch (EOFException eof) {
                Print.sysPrintln("");
                Print.sysPrintln("***** End-Of-File *****");
            } catch (IOException ioe) {
                Print.logException("Error reaading input file: " + parseFile, ioe);
            } finally {
                try { fis.close(); } catch (Throwable th) {/* ignore */}
            }

            /* done */
            return 0;

        }

        /* no options? */
        return _usage();

    }
	
	// ------------------------------------------------------------------------
	// ------------------------------------------------------------------------
	
	/**
    *** Main entry point used for debug purposes only
    **/
    public static void main(String argv[])
    {
        DBConfig.cmdLineInit(argv,false);
        TrackClientPacketHandler.configInit();
        System.exit(TrackClientPacketHandler._main(false));
    }
	
    // ------------------------------------------------------------------------
	// ------------------------------------------------------------------------
    
}
