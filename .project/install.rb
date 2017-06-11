require_relative "common/common"

def install(local, *args)
  c = Common.new
  if not local
    jars_volume_name = args.shift
    if jars_volume_name.nil?
      c.error "Missing docker volume name for storing JARS (e.g. \"jars\")"
      exit 1
    end
  end
  env = c.load_env
  cname = "#{env.namespace}-install"
  if local
    vol = "#{ENV["HOME"]}/.m2:/root/.m2"
  else
    vol = "#{jars_volume_name}:/root/.m2"
  end
  c.run_inline %W{
    docker create --name #{cname}
    -w /w
    -v #{vol}
    clojure:lein-alpine
    lein install
  }
  at_exit { c.run_inline %W{docker rm -f #{cname}} }
  env.source_file_paths.each do |src_path|
    c.pipe(
      %W{tar -c #{src_path}},
      %W{docker cp - #{cname}:/w}
    )
  end
  c.run_inline %W{docker start -a #{cname}}
end

Common.register_command({
  :invocation => "local-install",
  :description => "Installs this jar into the local system repository (#{ENV["HOME"]}/.m2).",
  :fn => Proc.new { |*args| install(true, *args) }
})

Common.register_command({
  :invocation => "docker-install",
  :description => "Installs this jar into the named docker volume at /root/.m2.",
  :fn => Proc.new { |*args| install(false, *args) }
})
