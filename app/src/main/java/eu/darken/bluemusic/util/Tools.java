package eu.darken.bluemusic.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import eu.darken.bluemusic.core.bluetooth.SourceDevice;

/**
 * Created by darken on 15/01/17.
 * TODO: Add description
 */
public class Tools {
    public static Map<String, SourceDevice> toMap(Collection<SourceDevice> sourceDevices) {
        Map<String, SourceDevice> deviceMap = new HashMap<>();
        for (SourceDevice sourceDevice : sourceDevices) {
            deviceMap.put(sourceDevice.getAddress(), sourceDevice);
        }
        return deviceMap;
    }
}
