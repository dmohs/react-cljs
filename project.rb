#!/usr/bin/env ruby

unless Dir.exists? ".project/common"
  system(*%W{mkdir -p .project})
  system(*%W{git clone https://github.com/dmohs/project-management.git .project/common})
end

require_relative ".project/common/common"
require_relative ".project/devserver"
require_relative ".project/docs"

c = Common.new

if ARGV.length == 0 or ARGV[0] == "--help"
  c.print_usage
  exit 0
end

command = ARGV.first
args = ARGV.drop(1)

c.handle_or_die(command, *args)
