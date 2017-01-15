package eu.darken.bluemusic.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import eu.darken.bluemusic.core.bluetooth.Device;

/**
 * Created by darken on 15/01/17.
 * TODO: Add description
 */
public class Tools {
    public static Map<String, Device> toMap(Collection<Device> devices) {
        Map<String, Device> deviceMap = new HashMap<>();
        for (Device device : devices) {
            deviceMap.put(device.getAddress(), device);
        }
        return deviceMap;
    }
}
