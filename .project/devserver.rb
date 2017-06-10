require_relative "common/common"
require "json"

def start_dev()
  c = Common.new
  env = c.load_env
  c.sf.maybe_start_file_syncing
  unless c.docker.image_exists?("dmohs/clojurescript")
    c.error "Pulling ClojureScript image..."
    c.run_inline %W{docker pull dmohs/clojurescript}
  end
  c.status "Starting figwheel. Wait for prompt before connecting with a browser..."
  docker_run = %W{
    docker run --name #{env.namespace}-figwheel
      --rm -it
      -w /w
      -p 3449:3449
      -v jars:/root/.m2
  }
  docker_run += c.sf.get_volume_mounts
  cmd = "sleep 1; rlwrap lein with-profile +ui figwheel"
  docker_run += %W{dmohs/clojurescript sh -c #{cmd}}
  c.run_inline docker_run
end

Common.register_command({
  :invocation => "start",
  :description => "Starts the development compiler and server.",
  :fn => Proc.new { |*args| start_dev(*args) }
})
