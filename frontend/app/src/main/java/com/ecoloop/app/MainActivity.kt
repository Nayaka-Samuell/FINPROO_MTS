package com.ecoloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.compose.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit

private val android.content.Context.store by preferencesDataStore("ecoloop_session")
private val TOKEN = stringPreferencesKey("jwt")
val districts=listOf("Jakarta Selatan","Jakarta Pusat","Jakarta Barat","Jakarta Timur","Jakarta Utara")
data class User(val id:String="",val name:String="",val email:String="",val contact:String?=null,val city:String="DKI Jakarta",val district:String="")
data class CategoryDto(val id:String,val name:String,val unit:String)
data class ListingDto(val id:String,val supplierId:String,val categoryId:String,val categoryName:String="Material",val title:String,val description:String,val totalKg:Double,val availableKg:Double,val district:String,val status:String,val photos:List<String> = emptyList()){val displayPhoto:String get()=photos.firstOrNull()?.takeIf{it.isNotBlank()}?:defaultPhotoForCategory(title,categoryId)}
data class ListingCreateRequest(val categoryId:String,val title:String,val description:String,val totalKg:Double,val district:String,val photos:List<String> = emptyList())
data class ReservationDto(val id:String,val listingId:String,val receiverId:String,val supplierId:String,val requestedKg:Double,val reservedKg:Double,val status:String,val createdAt:String,val updatedAt:String,val transferId:String?=null,val listingTitle:String="Material",val categoryName:String="Kategori",val listingDistrict:String="Jakarta",val supplierName:String="Penyedia",val receiverName:String="Penerima")
data class TransferDto(val id:String,val reservationId:String,val actualKg:Double,val completedAt:String,val status:String="COMPLETED")
data class ImpactDto(val totalKg:Double,val transferCount:Long,val suppliedKg:Double,val receivedKg:Double)
data class TokenDto(val token:String,val expiresAt:String)
data class DataResponse<T>(val data:T)
data class ListingPage(val items:List<ListingDto> = emptyList(),val page:Int=1,val limit:Int=20,val total:Int=0)
data class AuthRequest(val email:String,val password:String,val name:String?=null,val city:String?=null,val district:String?=null)
data class AuthResponse(val token:String="",val user:User?=null)
data class ReservationRequest(val listingId:String,val requestedKg:Double)
data class HandoverRequest(val handoverToken:String,val actualKg:Double)
fun defaultPhotoForCategory(title:String,categoryId:String):String{val key="$title $categoryId".lowercase();val id=when{listOf("kain","perca","fabric","textile").any{it in key}->"photo-1558618666-fcd25c85cd64";listOf("kardus","cardboard","karton").any{it in key}->"photo-1532996122724-e3c354a0b15b";listOf("botol","bottle","plastik","plastic").any{it in key}->"photo-1602143407151-7111542de6e8";listOf("kayu","wood","timber").any{it in key}->"photo-1513467655676-561b7d489a88";else->"photo-1558618666-fcd25c85cd64"};return "https://images.unsplash.com/$id?auto=format&fit=crop&w=1200&q=85"}
interface EcoApi{
 @POST("auth/login") suspend fun login(@Body body:AuthRequest):DataResponse<AuthResponse>
 @POST("auth/register") suspend fun register(@Body body:AuthRequest):DataResponse<AuthResponse>
 @GET("me") suspend fun me():DataResponse<User>
 @GET("categories") suspend fun categories():DataResponse<List<CategoryDto>>
 @GET("listings") suspend fun listings(@Query("q") q:String?=null,@Query("categoryId") categoryId:String?=null,@Query("district") district:String?=null):DataResponse<ListingPage>
 @GET("listings/{id}") suspend fun detail(@Path("id") id:String):DataResponse<ListingDto>
 @POST("listings") suspend fun addListing(@Body body:ListingCreateRequest):DataResponse<ListingDto>
 @POST("reservations") suspend fun reserve(@Body body:ReservationRequest):DataResponse<ReservationDto>
 @GET("reservations") suspend fun reservations(@Query("view") view:String):DataResponse<List<ReservationDto>>
 @POST("reservations/{id}/approve") suspend fun approve(@Path("id") id:String):DataResponse<ReservationDto>
 @POST("reservations/{id}/reject") suspend fun reject(@Path("id") id:String):DataResponse<ReservationDto>
 @POST("reservations/{id}/handover-token") suspend fun handover(@Path("id") id:String):DataResponse<TokenDto>
 @POST("reservations/{id}/complete") suspend fun complete(@Path("id") id:String,@Body body:HandoverRequest):DataResponse<TransferDto>
 @GET("impact/me") suspend fun impact():DataResponse<ImpactDto>
}
class EcoRepository(private val context:android.content.Context){private val client=OkHttpClient.Builder().addInterceptor(Interceptor{chain->val token=runCatching{kotlinx.coroutines.runBlocking{context.store.data.first()[TOKEN]}}.getOrNull();chain.proceed(chain.request().newBuilder().apply{if(!token.isNullOrBlank())addHeader("Authorization","Bearer $token")}.build())}).connectTimeout(15,TimeUnit.SECONDS).build();val api:EcoApi=Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(EcoApi::class.java);suspend fun saveToken(t:String){context.store.edit{it[TOKEN]=t}};suspend fun clear(){context.store.edit{it.remove(TOKEN)}};suspend fun hasToken()=context.store.data.first()[TOKEN]!=null}
class EcoViewModel(private val repo:EcoRepository):ViewModel(){
 private val _categories=MutableStateFlow<List<CategoryDto>>(emptyList());val categories=_categories.asStateFlow();private val _list=MutableStateFlow<List<ListingDto>>(emptyList());val list=_list.asStateFlow();private val _loading=MutableStateFlow(true);val loading=_loading.asStateFlow();private val _incoming=MutableStateFlow<List<ReservationDto>>(emptyList());val incoming=_incoming.asStateFlow();private val _outgoing=MutableStateFlow<List<ReservationDto>>(emptyList());val outgoing=_outgoing.asStateFlow();private val _impact=MutableStateFlow(ImpactDto(0.0,0,0.0,0.0));val impact=_impact.asStateFlow();private val _user=MutableStateFlow<User?>(null);val user=_user.asStateFlow();private val _error=MutableStateFlow<String?>(null);val error=_error.asStateFlow();private val _busy=MutableStateFlow(false);val busy=_busy.asStateFlow();private val _authenticated=MutableStateFlow(false);val authenticated=_authenticated.asStateFlow()
 init{viewModelScope.launch{_authenticated.value=repo.hasToken();runCatching{_categories.value=repo.api.categories().data};if(_authenticated.value){runCatching{_user.value=repo.api.me().data};refreshNow()}}}
 private fun friendly(e:Throwable):String{if(e !is HttpException)return e.message?:"Terjadi kendala. Coba lagi.";val message=runCatching{JsonParser.parseString(e.response()?.errorBody()?.string()).asJsonObject.getAsJsonObject("error").get("message")?.asString}.getOrNull();return message?:when(e.code()){401->"Sesi tidak valid. Silakan masuk kembali.";403->"Anda tidak memiliki izin untuk tindakan ini.";404->"Data tidak ditemukan.";409->"Stok atau status reservasi telah berubah.";else->"Permintaan gagal. Silakan coba lagi."}}
 private fun action(block:suspend()->Unit){viewModelScope.launch{_error.value=null;_busy.value=true;try{block()}catch(e:Throwable){_error.value=friendly(e)}finally{_busy.value=false}}}
 fun auth(email:String,password:String,name:String?=null,district:String?=null,register:Boolean=false)=action{val result=(if(register)repo.api.register(AuthRequest(email,password,name,"DKI Jakarta",district))else repo.api.login(AuthRequest(email,password))).data;check(result.token.isNotBlank()){"Token tidak diterima."};repo.saveToken(result.token);_user.value=result.user?:repo.api.me().data;_authenticated.value=true;refreshNow()}
 fun logout()=action{repo.clear();_authenticated.value=false;_user.value=null}
 fun refresh()=action{refreshNow()}
 private suspend fun refreshNow(){_loading.value=true;try{_list.value=repo.api.listings().data.items;_incoming.value=repo.api.reservations("incoming").data;_outgoing.value=repo.api.reservations("outgoing").data;_impact.value=repo.api.impact().data}finally{_loading.value=false}}
 fun search(q:String,categoryId:String?,district:String?)=action{_loading.value=true;_list.value=repo.api.listings(q.ifBlank{null},categoryId,district).data.items;_loading.value=false}
 suspend fun loadDetail(id:String)=repo.api.detail(id).data
 fun reserve(item:ListingDto,kg:Double,success:()->Unit={})=action{require(kg>0&&kg<=item.availableKg){"Masukkan berat lebih dari 0 dan tidak melebihi stok."};repo.api.reserve(ReservationRequest(item.id,kg));refreshNow();success()}
 fun addListing(body:ListingCreateRequest)=action{repo.api.addListing(body);refreshNow()}
 fun approve(id:String,yes:Boolean)=action{if(yes)repo.api.approve(id)else repo.api.reject(id);refreshNow()}
 fun handover(id:String,onToken:(TokenDto)->Unit)=action{onToken(repo.api.handover(id).data)}
 fun complete(id:String,token:String,kg:Double,onSuccess:()->Unit={})=action{require(token.isNotBlank()&&kg>0){"Token dan berat aktual wajib diisi."};repo.api.complete(id,HandoverRequest(token,kg));refreshNow();onSuccess()}
}
private val ecoColors=lightColorScheme(primary=Color(0xFF176347),onPrimary=Color.White,primaryContainer=Color(0xFFDCEFE3),onPrimaryContainer=Color(0xFF20332B),secondary=Color(0xFF278665),background=Color(0xFFF4F8F4),surface=Color.White,onBackground=Color(0xFF20332B),onSurface=Color(0xFF20332B))
@Composable fun EcoLoopTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme=ecoColors,shapes=Shapes(medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(22.dp)),content=content) }
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{EcoLoopTheme{val context=LocalContext.current;val vm:EcoViewModel=viewModel(factory=object:androidx.lifecycle.ViewModelProvider.Factory{override fun <T:ViewModel> create(c:Class<T>):T=EcoViewModel(EcoRepository(context)) as T});EcoApp(vm)}}}}
@Composable fun EcoApp(vm:EcoViewModel){
 val auth by vm.authenticated.collectAsState()
 val nav=rememberNavController()
 var selected by remember{mutableStateOf<ListingDto?>(null)}
 if(!auth){AuthScreen(vm);return}
 val routes=listOf("home","activity","impact","profile")
 val labels=listOf("Beranda","Aktivitas","Dampak","Profil")
 val icons=listOf(Icons.Default.Home,Icons.Default.List,Icons.Default.Insights,Icons.Default.Person)
 Scaffold(
  bottomBar={NavigationBar { routes.forEachIndexed { index, route -> NavigationBarItem(selected=nav.currentDestination?.route==route,onClick={nav.navigate(route){popUpTo("home");launchSingleTop=true}},icon={Icon(icons[index],null)},label={Text(labels[index])}) } }},
  floatingActionButton={if(nav.currentDestination?.route=="home") FloatingActionButton(onClick={nav.navigate("add")}) { Icon(Icons.Default.Add,"Bagikan material") }}
 ) { paddingValues ->
  NavHost(navController=nav,startDestination="home",modifier=Modifier.padding(paddingValues)) {
   composable("home"){HomeScreen(vm,{selected=it;nav.navigate("detail")},{nav.navigate("add")})}
   composable("detail"){selected?.let{DetailScreen(it,vm)}}
   composable("add"){AddListingScreen(vm)}
   composable("activity"){ActivityScreen(vm)}
   composable("impact"){ImpactScreen(vm)}
   composable("profile"){ProfileScreen(vm)}
  }
 }
}
@Composable private fun AuthScreen(vm:EcoViewModel){var register by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var district by remember{mutableStateOf(districts.first())};Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center){Text("EcoLoop",fontSize=36.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary);Text("Bahan sisa, peluang baru.");Spacer(Modifier.height(20.dp));if(register){EcoField("Nama",name){name=it};DistrictPicker(district){district=it}};EcoField("Email",email){email=it};EcoField("Password",password,true){password=it};val busy by vm.busy.collectAsState();EcoPrimaryButton(if(register)"Buat akun" else "Masuk",busy){vm.auth(email,password,name,district,register)};TextButton(onClick={register=!register}){Text(if(register)"Sudah punya akun? Masuk" else "Belum punya akun? Daftar")};ErrorText(vm)}}
