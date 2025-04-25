# Check that we have the correct number of arguments
if {[llength $argv] != 1} {
    puts "Usage: source program_fpga.tcl <bitstream_file>"
    return
}

# Get the bitstream file name from the argument
set bitstream_file [lindex $argv 0]

open_hw_manager
connect_hw_server -url localhost:3121
current_hw_target [get_hw_targets */Digilent/210308A11B15]
open_hw_target

# Program and Refresh the device
current_hw_device [lindex [get_hw_devices] 0]
refresh_hw_device -update_hw_probes false [lindex [get_hw_devices] 0]
set_property PROGRAM.FILE "${bitstream_file}" [lindex [get_hw_devices] 0]

program_hw_devices [lindex [get_hw_devices] 0]
refresh_hw_device [lindex [get_hw_devices] 0]
exit
