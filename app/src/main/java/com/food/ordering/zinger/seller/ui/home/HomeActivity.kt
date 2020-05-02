package com.food.ordering.zinger.seller.ui.home

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.amulyakhare.textdrawable.TextDrawable
import com.food.ordering.zinger.seller.R
import com.food.ordering.zinger.seller.data.local.PreferencesHelper
import com.food.ordering.zinger.seller.data.local.Resource
import com.food.ordering.zinger.seller.data.model.OrderNotificationPayload
import com.food.ordering.zinger.seller.data.model.ShopConfigurationModel
import com.food.ordering.zinger.seller.data.model.UserModel
import com.food.ordering.zinger.seller.databinding.ActivityHomeBinding
import com.food.ordering.zinger.seller.databinding.BottomSheetAccountSwitchBinding
import com.food.ordering.zinger.seller.databinding.HeaderLayoutBinding
import com.food.ordering.zinger.seller.ui.contactus.ContactUsActivity
import com.food.ordering.zinger.seller.ui.contributors.ContributorsActivity
import com.food.ordering.zinger.seller.ui.login.LoginActivity
import com.food.ordering.zinger.seller.ui.menu.MenuActivity
import com.food.ordering.zinger.seller.ui.orderhistory.OrderHistoryActivity
import com.food.ordering.zinger.seller.ui.profile.ProfileActivity
import com.food.ordering.zinger.seller.ui.seller.SellerActivity
import com.food.ordering.zinger.seller.ui.shopProfile.ShopProfileActivity
import com.food.ordering.zinger.seller.utils.AppConstants
import com.food.ordering.zinger.seller.utils.EventBus
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel

class HomeActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: OrderViewModel by viewModel()
    private val preferencesHelper: PreferencesHelper by inject()
    private lateinit var headerLayout: HeaderLayoutBinding
    private lateinit var drawer: Drawer
    private lateinit var progressDialog: ProgressDialog
    private lateinit var errorSnackBar: Snackbar
    private var shopConfig: ShopConfigurationModel? = null

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        shopConfig = preferencesHelper.getShop()!!
            .filter { it.shopModel.id == preferencesHelper.currentShop }[0]

        initView()
        setListeners()
        setupMaterialDrawer()
        setObservers()
        setupFCM()

        viewModel.getOrderByShopId(preferencesHelper.currentShop)
        viewModel.getShopDetail(preferencesHelper.currentShop)
        println("testing")
        subscribeToOrders()
    }


    @SuppressLint("SetTextI18n")
    private fun initView() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home)
        headerLayout = DataBindingUtil.inflate(
            LayoutInflater.from(applicationContext),
            R.layout.header_layout,
            null,
            false
        )

        errorSnackBar = Snackbar.make(binding.root, "", Snackbar.LENGTH_INDEFINITE)
        val snackButton: Button = errorSnackBar.view.findViewById(R.id.snackbar_action)
        snackButton.setCompoundDrawables(null, null, null, null)
        snackButton.background = null
        snackButton.setTextColor(ContextCompat.getColor(applicationContext, R.color.accent))
        binding.imageMenu.setOnClickListener(this)
        binding.textShopName.text = shopConfig?.shopModel?.name
        binding.textShopRating.text =
            shopConfig?.ratingModel?.rating.toString() + " (" + shopConfig?.ratingModel?.userCount + ")"
        progressDialog = ProgressDialog(this)


        var fragment = Fragment()
        preferencesHelper.role?.let {
            if (it == AppConstants.ROLE.DELIVERY.name) {
                binding.tabs.visibility = View.GONE
                fragment = ReadyFragment()
            } else {
                binding.tabs.visibility = View.VISIBLE
                fragment = NewOrdersFragment()
            }
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out,
                R.anim.fade_in,
                R.anim.fade_out
            )
            .replace(R.id.container, fragment, fragment.javaClass.simpleName)
            .commit()

        setStatusBarHeight()

        Picasso.get().load(shopConfig?.shopModel?.photoUrl).placeholder(R.drawable.ic_shop)
            .into(binding.imageCompany)
    }

    private fun setListeners() {
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        val fragment = NewOrdersFragment()
                        supportFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.fade_in,
                                R.anim.fade_out,
                                R.anim.fade_in,
                                R.anim.fade_out
                            )
                            .replace(R.id.container, fragment, fragment.javaClass.simpleName)
                            .commit()
                    }
                    1 -> {
                        val fragment = PreparingFragment()
                        supportFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.fade_in,
                                R.anim.fade_out,
                                R.anim.fade_in,
                                R.anim.fade_out
                            )
                            .replace(R.id.container, fragment, fragment.javaClass.simpleName)
                            .commit()
                    }
                    2 -> {
                        val fragment = ReadyFragment()
                        supportFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.fade_in,
                                R.anim.fade_out,
                                R.anim.fade_in,
                                R.anim.fade_out
                            )
                            .replace(R.id.container, fragment, fragment.javaClass.simpleName)
                            .commit()
                    }
                }
            }
        })

        binding.imageCompany.setOnClickListener {
            showAccountListBottomSheet()
        }
    }

    private fun setStatusBarHeight() {
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val rectangle = Rect()
                val window = window
                window.decorView.getWindowVisibleDisplayFrame(rectangle)
                val statusBarHeight = rectangle.top
                val layoutParams = headerLayout.statusbarSpaceView.layoutParams
                layoutParams.height = statusBarHeight
                headerLayout.statusbarSpaceView.layoutParams = layoutParams
                Log.d("Home", "status bar height $statusBarHeight")
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun updateHeaderLayoutUI() {
        headerLayout.textCustomerName.text = preferencesHelper.name
        headerLayout.textEmail.text = preferencesHelper.email
        val textDrawable = TextDrawable.builder()
            .buildRound(
                preferencesHelper.name?.get(0).toString().capitalize(),
                ContextCompat.getColor(this, R.color.accent)
            )
        headerLayout.imageProfilePic.setImageDrawable(textDrawable)

    }

    private fun setupMaterialDrawer() {
        var identifier = 0L
        val profileItem = PrimaryDrawerItem().withIdentifier(++identifier).withName("My Profile")
            .withIcon(R.drawable.ic_drawer_user)
        val shopProfileItem =
            PrimaryDrawerItem().withIdentifier(++identifier).withName("Shop Profile")
                .withIcon(R.drawable.ic_home)
        val ordersItem = PrimaryDrawerItem().withIdentifier(++identifier).withName("Past Orders")
            .withIcon(R.drawable.ic_drawer_past_rides)
        val menuItem = PrimaryDrawerItem().withIdentifier(++identifier).withName("Shop Menu")
            .withIcon(R.drawable.ic_drawer_order)
        val sellerItem = PrimaryDrawerItem().withIdentifier(++identifier).withName("Employees")
            .withIcon(R.drawable.ic_employee)
        val contactUsItem = PrimaryDrawerItem().withIdentifier(++identifier).withName("Contact Us")
            .withIcon(R.drawable.ic_drawer_mail)
        val signOutItem = PrimaryDrawerItem().withIdentifier(++identifier).withName("Sign out")
            .withIcon(R.drawable.ic_drawer_log_out)
        val contributorsItem = PrimaryDrawerItem().withIdentifier(++identifier).withName("Contributors")
            .withIcon(R.drawable.ic_drawer_info)

        drawer = DrawerBuilder()
            .withActivity(this)
            .withDisplayBelowStatusBar(false)
            .withHeader(headerLayout.root)
            .withTranslucentStatusBar(true)
            .withCloseOnClick(true)
            .withSelectedItem(-1)
            .addDrawerItems(
                profileItem,
                shopProfileItem,
                ordersItem,
                menuItem,
                sellerItem,
                contributorsItem,
                contactUsItem,
                DividerDrawerItem(),
                signOutItem
            )
            .withOnDrawerItemClickListener { _, _, drawerItem ->
                if (profileItem.identifier == drawerItem.identifier) {
                    startActivity(Intent(applicationContext, ProfileActivity::class.java))
                }
                if (shopProfileItem.identifier == drawerItem.identifier) {
                    startActivity(Intent(applicationContext, ShopProfileActivity::class.java))
                }
                if (ordersItem.identifier == drawerItem.identifier) {
                    startActivity(Intent(applicationContext, OrderHistoryActivity::class.java))
                }
                if (menuItem.identifier == drawerItem.identifier) {
                    startActivity(Intent(applicationContext, MenuActivity::class.java))
                }
                if (sellerItem.identifier == drawerItem.identifier) {
                    startActivity(Intent(applicationContext, SellerActivity::class.java))
                }
                if (contributorsItem.identifier == drawerItem.identifier) {
                    startActivity(Intent(applicationContext, ContributorsActivity::class.java))
                }
                if (contactUsItem.identifier == drawerItem.identifier) {
                    startActivity(Intent(applicationContext,ContactUsActivity::class.java))
                }
                if (signOutItem.identifier == drawerItem.identifier) {
                    MaterialAlertDialogBuilder(this@HomeActivity)
                        .setTitle("Confirm Sign Out")
                        .setMessage("Are you sure want to sign out?")
                        .setPositiveButton("Yes") { _, _ ->
                            FirebaseAuth.getInstance().signOut()
                            preferencesHelper.getShop()?.forEach {
                                FirebaseMessaging.getInstance()
                                    .unsubscribeFromTopic(
                                        AppConstants.NOTIFICATION_TOPIC_SHOP_ZINGER + it.shopModel.id
                                    );
                            }
                            FirebaseMessaging.getInstance()
                                .unsubscribeFromTopic(AppConstants.NOTIFICATION_TOPIC_GLOBAL);
                            preferencesHelper.clearPreferences()
                            startActivity(Intent(applicationContext, LoginActivity::class.java))
                            finish()
                        }
                        .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
                true
            }
            .build()

        preferencesHelper.role?.let { role ->
            if (role == AppConstants.ROLE.SELLER.name) {
                drawer.removeItem(sellerItem.identifier)
            } else if (role == AppConstants.ROLE.DELIVERY.name) {
                drawer.removeItem(shopProfileItem.identifier)
                drawer.removeItem(menuItem.identifier)
                drawer.removeItem(sellerItem.identifier)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setObservers() {

        viewModel.updateFcmTokenResponse.observe(this, Observer { resource ->
            if (resource != null) {
                when (resource.status) {
                    Resource.Status.SUCCESS -> {
                        println("FCM token Succesfully updated")
                    }
                    Resource.Status.ERROR -> {
                        preferencesHelper.fcmToken = ""
                    }
                    Resource.Status.OFFLINE_ERROR -> {
                        preferencesHelper.fcmToken = ""
                    }
                }
            }
        })

        viewModel.getShopDetailResponse.observe(this, Observer { resource ->
            if (resource != null) {
                when (resource.status) {
                    Resource.Status.SUCCESS -> {
                        if (resource.data?.data != null) {
                            val latestShopDetail = resource.data.data
                            val shopConfigurationList = preferencesHelper.getShop()
                            if (shopConfigurationList != null) {
                                for (shop in shopConfigurationList) {
                                    if (shop.shopModel.id == latestShopDetail.shopModel.id)
                                        shop.shopModel = latestShopDetail.shopModel
                                    shop.configurationModel = latestShopDetail.configurationModel
                                    shop.ratingModel = latestShopDetail.ratingModel
                                }
                                preferencesHelper.shop = Gson().toJson(shopConfigurationList)
                                shopConfig = preferencesHelper.getShop()!!
                                    .filter { it.shopModel.id == preferencesHelper.currentShop }[0]
                                binding.textShopName.text = shopConfig?.shopModel?.name
                                binding.textShopRating.text =
                                    shopConfig?.ratingModel?.rating.toString() + " (" + shopConfig?.ratingModel?.userCount + ")"
                            }
                        }
                    }
                    Resource.Status.ERROR -> {
                        Toast.makeText(
                            this,
                            "Failed to fetch latest shop details",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    Resource.Status.OFFLINE_ERROR -> {
                        Toast.makeText(this, "Device Offline", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })

    }

    lateinit var accountAdapter: AccountAdapter
    private fun showAccountListBottomSheet() {
        val dialogBinding: BottomSheetAccountSwitchBinding =
            DataBindingUtil.inflate(
                layoutInflater,
                R.layout.bottom_sheet_account_switch,
                null,
                false
            )

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.show()

        val accountList: ArrayList<ShopConfigurationModel> = ArrayList()
        preferencesHelper.getShop()?.let {

            accountList.addAll(it)
            for (i in accountList.indices) {
                accountList[i].isSelected =
                    accountList[i].shopModel.id == preferencesHelper.currentShop
            }
        }
        accountAdapter = AccountAdapter(accountList, object : AccountAdapter.OnItemClickListener {
            @SuppressLint("SetTextI18n")
            override fun onItemClick(item: ShopConfigurationModel, position: Int) {
                for (i in accountList.indices) {
                    accountList[i].isSelected = false
                }
                accountList[position].isSelected = true
                accountAdapter.notifyDataSetChanged()

                accountList[position].shopModel.id?.let {
                    shopConfig = accountList[position]
                    preferencesHelper.currentShop = it
                    Picasso.get().load(accountList[position].shopModel.photoUrl)
                        .placeholder(R.drawable.ic_shop)
                        .into(binding.imageCompany)
                    binding.textShopName.text = accountList[position].shopModel.name
                    binding.textShopRating.text =
                        accountList[position].ratingModel.rating.toString() + " (" + accountList[position].ratingModel.userCount + ")"
                    this@HomeActivity.recreate()
                    //viewModel.getOrderByShopId(it)
                }

                dialog.dismiss()
            }
        })
        dialogBinding.recyclerAccounts.layoutManager = LinearLayoutManager(this)
        dialogBinding.recyclerAccounts.adapter = accountAdapter
    }


    override fun onClick(v: View) {
        when (v.id) {
            R.id.image_menu -> {
                drawer.openDrawer()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()

        try {
            preferencesHelper.orderStatusChanged.let {
                if (!it)
                    viewModel.getOrderByShopId(preferencesHelper.currentShop)
            }
        } catch (e: Exception) {
            println(e.printStackTrace())
        }


        shopConfig = preferencesHelper.getShop()!!
            .filter { it.shopModel.id == preferencesHelper.currentShop }[0]
        binding.textShopName.text = shopConfig?.shopModel?.name
        binding.textShopRating.text =
            shopConfig?.ratingModel?.rating.toString() + " (" + shopConfig?.ratingModel?.userCount + ")"
        updateHeaderLayoutUI()
    }


    override fun onBackPressed() {
        MaterialAlertDialogBuilder(this@HomeActivity)
            .setTitle("Exit app?")
            .setMessage("Are you sure want to exit the app?")
            .setPositiveButton("Yes") { dialog, _ ->
                super.onBackPressed()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun setupFCM() {
        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCM", "getInstanceId failed", task.exception)
                    return@OnCompleteListener
                }
                // Get new Instance ID token
                val token = task.result?.token
                if (preferencesHelper.fcmToken != token ) {
                    preferencesHelper.fcmToken = token
                    preferencesHelper.fcmToken?.let { fcmToken ->
                        val fcmTokenRequest = UserModel(
                            id = preferencesHelper.id,
                            notificationToken = preferencesHelper.fcmToken
                        )
                        viewModel.updateFCMToken(fcmTokenRequest)
                    }
                }
            })


        if (preferencesHelper.isFCMTopicSubScribed == null || preferencesHelper.isFCMTopicSubScribed == false) {
            preferencesHelper.getShop()?.forEach {
                FirebaseMessaging.getInstance()
                    .subscribeToTopic(AppConstants.NOTIFICATION_TOPIC_SHOP_ZINGER + it.shopModel.id);
            }
            FirebaseMessaging.getInstance()
                .subscribeToTopic(AppConstants.NOTIFICATION_TOPIC_GLOBAL)
            preferencesHelper.isFCMTopicSubScribed = true
        }

    }

    @ExperimentalCoroutinesApi
    private fun subscribeToOrders() {
        val subscription = EventBus.asChannel<OrderNotificationPayload>()
        CoroutineScope(Dispatchers.Main).launch {
            subscription.consumeEach {
                println("Received order status event")
                viewModel.getOrderByShopId(preferencesHelper.currentShop)
            }
        }
    }
}
