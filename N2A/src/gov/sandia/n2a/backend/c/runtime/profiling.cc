#include<dlfcn.h>
#include <string>
#include <iostream>
void(*push_region_cb)(const char*);
void(*pop_region_cb)();
void(*init_cb)(int loadseq, uint64_t version, uint32_t ndevinfos, void* devinfos);
void(*finalize_cb)();
void push_region(const std::string& kName) {
  if ( push_region_cb != nullptr) {
    (*push_region_cb)(kName.c_str());
  }
}

void pop_region() {
  if (pop_region_cb != nullptr) {
    (*pop_region_cb)();
  }
}
void get_callbacks() {
      char* x = getenv("KOKKOS_PROFILE_LIBRARY");
      void* firstProfileLibrary = nullptr;
      if(x!=nullptr) {
         firstProfileLibrary = dlopen(x, RTLD_NOW | RTLD_GLOBAL);
      } else{
	 // TODO: smrt error
      }
      if(firstProfileLibrary!=nullptr){
        void* p9 = dlsym(firstProfileLibrary, "kokkosp_push_profile_region");
        push_region_cb=
            *reinterpret_cast<decltype(push_region_cb)*>(&p9);
        void* p10 = dlsym(firstProfileLibrary, "kokkosp_pop_profile_region");
        pop_region_cb = 
            *reinterpret_cast<decltype(pop_region_cb)*>(&p10);

        void* p11 = dlsym(firstProfileLibrary, "kokkosp_init_library");
        init_cb = 
            *reinterpret_cast<decltype(init_cb)*>(&p11);
        void* p12 = dlsym(firstProfileLibrary, "kokkosp_finalize_library");
        finalize_cb = 
            *reinterpret_cast<decltype(finalize_cb)*>(&p12);
         (*init_cb)(0,0,0,nullptr);
      }
      else {
	      std::cout << "Bad dlopen\n";
      }
}
void finalize_profiling() {
  (*finalize_cb)();
}

